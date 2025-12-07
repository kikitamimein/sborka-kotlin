
import pandas as pd
import os

file_path = "озон омск 233 сорт.xlsx"
try:
    df = pd.read_excel(file_path, header=None, engine='openpyxl')
    print("Row 4 (Index 4):")
    print(df.iloc[4].tolist())
    print("\nRow 3 (Index 3):")
    print(df.iloc[3].tolist())
except Exception as e:
    print(f"Error: {e}")
