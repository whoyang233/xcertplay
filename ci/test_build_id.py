import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location('build_id', Path(__file__).with_name('prepare-build-id.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class BuildIdTest(unittest.TestCase):
    def test_first_build_and_server_floor(self):
        self.assertEqual('2026111001', module.allocate('20261110', 1, []))
        self.assertEqual('2026111002', module.allocate('20261110', 1, ['2026111001']))

    def test_workflow_order_survives_unpublished_and_failed_attempts(self):
        self.assertEqual('2026111005', module.allocate('20261110', 5, ['2026111001']))

    def test_artifacts_prevent_reusing_an_unpublished_build(self):
        self.assertEqual('2026111008', module.allocate('20261110', 2, ['2026111007', '2026111004']))

    def test_new_day_restarts_sequence(self):
        self.assertEqual('2026111101', module.allocate('20261111', 1, ['2026111099']))

    def test_invalid_ids_do_not_reserve_sequences(self):
        for value in ['2026023001', '2026111000', '20261110100', '../2026111001', '2100010101']:
            self.assertFalse(module.valid_build_id(value))
        self.assertTrue(module.valid_build_id('2028022901'))

    def test_two_digit_daily_limit_is_explicit(self):
        with self.assertRaises(ValueError):
            module.allocate('20261110', 100, [])
        with self.assertRaises(ValueError):
            module.allocate('20261110', 1, ['2026111099'])

if __name__ == '__main__':
    unittest.main()
