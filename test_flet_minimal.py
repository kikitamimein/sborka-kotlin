
import flet as ft

def main(page: ft.Page):
    page.add(ft.Text("Hello, Flet!"))

if __name__ == "__main__":
    try:
        ft.app(target=main, view=ft.AppView.WEB_BROWSER)
    except Exception as e:
        print(f"Error: {e}")
