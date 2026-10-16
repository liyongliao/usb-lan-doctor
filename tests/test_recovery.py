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
        self.script.write_text(SCRIPT.read_text().replace('/sys/bus/usb',str(self.sys)))
        self.env=dict(os.environ,PATH=str(self.bin)+':'+os.environ['PATH'],CALL_LOG=str(self.base/'calls'))
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
        for action in ['reauth','power','auto','probe']: self.run_action(action,code=10)
        self.assertEqual((self.dev/'authorized').read_text().strip(),'1');self.assertEqual((self.dev/'power/control').read_text().strip(),'auto')
    def test_port_prefix_cannot_match_another_port(self):
        self.env['ACTUAL_PORT']='port01';self.run_action('host',name='port0',code=12)
    def test_system_role_request_failure_not_success(self):
        self.env['FAIL_HOST']='1';self.run_action('host',name='port0',code=14)
if __name__=='__main__': unittest.main(verbosity=2)
