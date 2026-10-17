"""Exercise the shipped recovery script against an isolated fake sysfs tree."""
import os, pathlib, subprocess, tempfile, unittest
SCRIPT = pathlib.Path(__file__).resolve().parents[1] / 'assets/recover.sh'
class RecoveryTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.base = pathlib.Path(self.tmp.name)
        self.sys = self.base/'sys'
        self.dev = self.sys/'devices/1-1'
        (self.dev/'power').mkdir(parents=True)
        (self.sys/'drivers').mkdir()
        for f,v in {'idVendor':'0bda','idProduct':'8156','bDeviceClass':'00','authorized':'1','power/control':'auto'}.items():
            (self.dev/f).write_text(v+'\n')
        self.bin=self.base/'bin'; self.bin.mkdir()
        for name,body in {'id':'echo ${FAKE_UID:-0}', 'sleep':':', 'dumpsys':'echo "$*" >> "$CALL_LOG"; echo "is_simulation_active=${SIMULATED:-false}"; echo "id=${ACTUAL_PORT:-port0}"; if [ "${FAIL_HOST:-0}" = 1 ] && [ "${2:-}" = set-port-roles ]; then exit 1; fi'}.items():
            p=self.bin/name; p.write_text('#!/bin/sh\n'+body+'\n'); p.chmod(0o755)
        self.script=self.base/'recover.sh'
        self.script.write_text(SCRIPT.read_text().replace('/sys/bus/usb',str(self.sys)).replace('/sys/class/net',str(self.base/'net')))
        self.env=dict(os.environ,PATH=str(self.bin)+':'+os.environ['PATH'],CALL_LOG=str(self.base/'calls'),NET_ROOT=str(self.base/'net'))
        ip=self.bin/'ip'
        ip.write_text('''#!/bin/sh
echo "ip $*" >> "$CALL_LOG"
if [ "$1" = link ]; then
 n="$NET_ROOT/$4"
 if [ "$5" = down ]; then
  [ "${FAIL_DOWN:-0}" = 1 ] && exit 1
  echo 0x1002 > "$n/flags"
  [ "${REPLACE_ON_DOWN:-0}" = 1 ] && echo 99 > "$n/ifindex"
 else
  if [ "${FAIL_UP_ONCE:-0}" = 1 ] && [ ! -f "$NET_ROOT/failed" ]; then touch "$NET_ROOT/failed"; exit 1; fi
  echo 0x1003 > "$n/flags"
 fi
fi
exit 0
''');ip.chmod(0o755)
    def tearDown(self): self.tmp.cleanup()
    def run_action(self,action,name='1-1',identity='0bda:8156',code=0):
        p=subprocess.run(['/bin/sh',str(self.script),action,name,identity],env=self.env,capture_output=True,text=True)
        self.assertEqual(p.returncode,code,p.stdout+p.stderr)
        return p
    def test_identity_mismatch(self):
        self.run_action('power',identity='0bda:8153',code=19)
        self.assertEqual((self.dev/'power/control').read_text().strip(),'auto')
    def test_hub_rejected(self):
        (self.dev/'bDeviceClass').write_text('09\n'); self.run_action('reauth',code=20)
    def test_non_network_rejected(self):
        (self.dev/'idProduct').write_text('9999\n'); self.run_action('power',identity='0bda:9999',code=21)
    def test_path_traversal_rejected(self): self.run_action('reauth',name='../../foo',code=17)
    def test_root_hub_address_rejected(self): self.run_action('reauth',name='usb1',code=17)
    def test_missing_device_rejected(self): self.run_action('reauth',name='1-8',code=18)
    def test_power_and_auto(self):
        self.run_action('power'); self.assertEqual((self.dev/'power/control').read_text().strip(),'on')
        self.run_action('auto'); self.assertEqual((self.dev/'power/control').read_text().strip(),'auto')
    def test_reauth_restores(self):
        self.run_action('reauth'); self.assertEqual((self.dev/'authorized').read_text().strip(),'1')
    def test_probe_skips_bound_interface(self):
        iface=self.dev/'1-1:1.0';iface.mkdir();(iface/'driver').symlink_to(self.sys/'drivers')
        self.run_action('probe'); self.assertFalse((self.sys/'drivers_probe').exists())
    def test_probe_unbound_interface(self):
        (self.dev/'1-1:1.0').mkdir(); self.run_action('probe')
        self.assertEqual((self.sys/'drivers_probe').read_text().strip(),'1-1:1.0')
    def test_unknown_action_rejected(self): self.run_action('reboot',code=16)
    def test_invalid_port_rejected(self): self.run_action('host',name='port0;reboot',code=11)
    def test_unknown_port_rejected(self): self.run_action('host',name='port9',code=12)
    def test_simulated_port_rejected(self):
        self.env['SIMULATED']='true'; self.run_action('host',name='port0',code=13)
    def test_real_port_request(self): self.run_action('host',name='port0')
    def test_ordinary_app_cannot_recover(self):
        self.env['FAKE_UID']='10300'; self.run_action('host',name='port0',code=10);self.assertFalse((self.base/'calls').exists())
    def test_adb_can_request_port_roles(self):
        self.env['FAKE_UID']='2000';self.run_action('host',name='port0');self.assertIn('set-port-roles port0 source host',(self.base/'calls').read_text())
    def test_adb_can_cycle_data_and_reenable(self):
        self.env['FAKE_UID']='2000';self.run_action('cycle',name='port0');calls=(self.base/'calls').read_text();self.assertLess(calls.index('port0 false'),calls.index('port0 true'))
    def test_adb_denied_all_kernel_writes(self):
        self.env['FAKE_UID']='2000'
        for action in ['reauth','power','auto','probe','linkup','linkreset']: self.run_action(action,code=10)
        self.assertEqual((self.dev/'authorized').read_text().strip(),'1');self.assertEqual((self.dev/'power/control').read_text().strip(),'auto')
    def test_port_prefix_cannot_match_another_port(self):
        self.env['ACTUAL_PORT']='port01';self.run_action('host',name='port0',code=12)
    def test_system_role_request_failure_not_success(self):
        self.env['FAIL_HOST']='1';self.run_action('host',name='port0',code=14)
    def add_net(self, name='eth0', flags='0x1003'):
        iface=self.dev/'1-1:1.0';(iface/'net'/name).mkdir(parents=True,exist_ok=True)
        n=self.base/'net'/name;n.mkdir(parents=True)
        (n/'device').symlink_to(iface)
        for f,v in {'ifindex':'12','flags':flags,'carrier':'0','operstate':'down','speed':'-1'}.items(): (n/f).write_text(v+'\n')
        return n
    def test_enable_only_selected_usb_interface(self):
        n=self.add_net(flags='0x1002');self.run_action('linkup')
        calls=(self.base/'calls').read_text();self.assertIn('ip link set dev eth0 up',calls);self.assertNotIn(' down',calls)
        self.assertEqual((n/'flags').read_text().strip(),'0x1003')
    def test_restart_interface_down_then_up_without_claiming_connected(self):
        self.add_net();p=self.run_action('linkreset');calls=(self.base/'calls').read_text()
        self.assertLess(calls.index('eth0 down'),calls.index('eth0 up'));self.assertIn('carrier=0',p.stdout)
        self.assertIn('不将命令成功等同于网络恢复',p.stdout)
    def test_link_no_interface(self): self.run_action('linkreset',code=26)
    def test_link_multiple_interfaces_rejected(self):
        self.add_net();self.add_net('eth1');self.run_action('linkreset',code=26);self.assertFalse((self.base/'calls').exists())
    def test_link_non_usb_target_rejected(self):
        n=self.add_net();(n/'device').unlink();(n/'device').symlink_to(self.base)
        self.run_action('linkreset',code=28);self.assertFalse((self.base/'calls').exists())
    def test_link_up_failure_restores_original_up(self):
        n=self.add_net();self.env['FAIL_UP_ONCE']='1';self.run_action('linkreset',code=30)
        self.assertEqual((n/'flags').read_text().strip(),'0x1003')
        self.assertEqual((self.base/'calls').read_text().count('eth0 up'),2)
    def test_link_initial_down_is_left_down_on_failure(self):
        n=self.add_net(flags='0x1002');self.env['FAIL_UP_ONCE']='1';self.run_action('linkreset',code=30)
        self.assertEqual((n/'flags').read_text().strip(),'0x1002')
    def test_link_replacement_during_restart_never_brought_up(self):
        self.add_net();self.env['REPLACE_ON_DOWN']='1';self.run_action('linkreset',code=28)
        self.assertNotIn('eth0 up',(self.base/'calls').read_text())
    def test_link_down_failure_restores_and_reports_failure(self):
        self.add_net();self.env['FAIL_DOWN']='1';self.run_action('linkreset',code=29)
        self.assertIn('eth0 up',(self.base/'calls').read_text())
    def test_link_invalid_flags_rejected(self):
        self.add_net(flags='garbage');self.run_action('linkreset',code=28);self.assertFalse((self.base/'calls').exists())
    def test_link_invalid_name_rejected(self):
        self.add_net(name='eth0;bad');self.run_action('linkreset',code=27);self.assertFalse((self.base/'calls').exists())
    def test_link_wrong_usb_identity_rejected(self):
        self.add_net();self.run_action('linkreset',identity='0bda:8153',code=19);self.assertFalse((self.base/'calls').exists())
if __name__=='__main__': unittest.main(verbosity=2)
