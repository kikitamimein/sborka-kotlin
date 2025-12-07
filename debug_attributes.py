
from excel_processor import ExcelProcessor
import inspect

print("Inspecting ExcelProcessor class...")
if hasattr(ExcelProcessor, 'process_file'):
    print("SUCCESS: 'process_file' exists in ExcelProcessor.")
else:
    print("FAILURE: 'process_file' NOT found in ExcelProcessor.")

print("\nMethods in ExcelProcessor:")
for name, method in inspect.getmembers(ExcelProcessor, predicate=inspect.isfunction):
    print(f"- {name}")
