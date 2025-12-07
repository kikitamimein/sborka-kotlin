
import flet as ft

print(f"Flet version: {getattr(ft, 'version', 'unknown')}")

def check_attr(name):
    if hasattr(ft, name):
        print(f"SUCCESS: ft.{name} exists")
    elif hasattr(ft, name.capitalize()):
        print(f"SUCCESS: ft.{name.capitalize()} exists (Capitalized)")
    else:
        print(f"FAILURE: ft.{name} NOT found")

check_attr('colors')
check_attr('Colors')
check_attr('icons')
check_attr('Icons')
check_attr('padding')
check_attr('Padding')
check_attr('border_radius')
check_attr('BorderRadius')
check_attr('alignment')
check_attr('Alignment')
