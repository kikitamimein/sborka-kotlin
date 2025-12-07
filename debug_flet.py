
import flet as ft
import sys

print(f"Flet version: {getattr(ft, 'version', 'unknown')}")

if hasattr(ft, 'icons'):
    print("SUCCESS: ft.icons exists")
elif hasattr(ft, 'Icons'):
    print("SUCCESS: ft.Icons exists")
else:
    print("FAILURE: neither ft.icons nor ft.Icons found")
    # Search for anything looking like icons
    matches = [a for a in dir(ft) if 'icon' in a.lower()]
    print(f"Potential matches: {matches}")
