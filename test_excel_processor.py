
import unittest
import os
from excel_processor import ExcelProcessor, ExcelWriter
import pandas as pd

class TestExcelProcessor(unittest.TestCase):
    def setUp(self):
        self.test_file = "озон омск 233 сорт.xlsx"
        # Ensure the file exists (it should be in the workspace)
        if not os.path.exists(self.test_file):
            self.skipTest(f"Test file {self.test_file} not found")

    def test_process_file(self):
        processor = ExcelProcessor(self.test_file)
        orders, shipment_info = processor.process_file()
        
        print(f"Shipment Info: {shipment_info}")
        print(f"Found {len(orders)} orders")
        
        self.assertTrue(len(orders) > 0)
        self.assertIn("Отгрузка", shipment_info)
        
        # Check first order structure
        first_order = orders[0]
        self.assertIn("name", first_order)
        self.assertIn("quantity", first_order)
        self.assertIn("article", first_order)
        self.assertIn("location", first_order)
        self.assertIn("barcode", first_order)
        
        print("First order sample:", first_order)

    def test_writer(self):
        # Create dummy data
        collected_data = [
            {'box': 1, 'article': 'A1', 'name': 'Item 1', 'quantity': 5, 'barcode': '1234567890123'},
            {'box': 1, 'article': 'A2', 'name': 'Item 2', 'quantity': 2, 'barcode': '1234567890124'},
            {'box': 2, 'article': 'A3', 'name': 'Item 3', 'quantity': 1, 'barcode': '1234567890125'}
        ]
        shipment_info = "Test Shipment"
        discrepancies = ["Missing Item X"]
        
        writer = ExcelWriter(collected_data, shipment_info, discrepancies, self.test_file)
        output_file = writer.generate_final_file()
        
        print(f"Generated file: {output_file}")
        self.assertTrue(os.path.exists(output_file))
        
        # Verify content
        df = pd.read_excel(output_file, header=None) # Read without header to check structure
        # We expect "Расхождения" at row 3 (index 2)
        self.assertEqual(df.iloc[2, 0], "Расхождения:")
        self.assertEqual(df.iloc[3, 0], "Missing Item X")
        
        # Clean up
        if os.path.exists(output_file):
            os.remove(output_file)

if __name__ == '__main__':
    unittest.main()
