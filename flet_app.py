
import flet as ft
from excel_processor import ExcelProcessor, ExcelWriter
import pickle
from pathlib import Path

class AssemblyApp:
    def __init__(self, page: ft.Page):
        self.page = page
        self.page.title = "Offline Assembler"
        self.page.theme_mode = ft.ThemeMode.DARK
        self.page.padding = 0
        self.page.window_width = 400
        self.page.window_height = 800
        
        # --- Colors ---
        self.COLOR_BG = "#1a1a1a"
        self.COLOR_SURFACE = "#2d2d2d"
        self.COLOR_PRIMARY = "#00e5ff" # Cyan
        self.COLOR_ACCENT = "#ff9100" # Orange
        self.COLOR_SUCCESS = "#00e676" # Green
        self.COLOR_TEXT = "#ffffff"
        self.COLOR_TEXT_SEC = "#aaaaaa"

        self.page.bgcolor = self.COLOR_BG
        
        # --- State ---
        self.assembly_items = []
        self.current_item_index = 0
        self.current_box = 1
        self.shipment_info = ""
        self.input_file_path = ""
        self.output_directory = str(Path.home() / "Downloads")  # Default to Downloads folder

        # --- UI Components ---
        self.file_picker = ft.FilePicker(on_result=self.on_file_picked)
        self.page.overlay.append(self.file_picker)
        
        self.save_file_picker = ft.FilePicker(on_result=self.on_save_file_picked)
        self.page.overlay.append(self.save_file_picker)
        
        self.folder_picker = ft.FilePicker(on_result=self.on_folder_picked)
        self.page.overlay.append(self.folder_picker)

        self.init_ui()

    def init_ui(self):
        self.page.clean()
        
        # Welcome Screen
        self.welcome_view = ft.Container(
            content=ft.Column(
                controls=[
                    ft.Icon(ft.Icons.INVENTORY_2_OUTLINED, size=100, color=self.COLOR_PRIMARY),
                    ft.Text("Offline Assembler", size=30, weight=ft.FontWeight.BOLD, color=self.COLOR_TEXT),
                    ft.Text("Загрузите файл для начала", size=16, color=self.COLOR_TEXT_SEC),
                    ft.Container(height=20),
                    ft.ElevatedButton(
                        "Открыть Excel",
                        icon=ft.Icons.UPLOAD_FILE,
                        style=ft.ButtonStyle(
                            color=self.COLOR_BG,
                            bgcolor=self.COLOR_PRIMARY,
                            padding=20,
                            shape=ft.RoundedRectangleBorder(radius=10),
                        ),
                        on_click=lambda _: self.file_picker.pick_files(allow_multiple=False, allowed_extensions=["xlsx", "xls"])
                    ),
                    ft.Container(height=10),
                    ft.ElevatedButton(
                        "Загрузить сессию",
                        icon=ft.Icons.RESTORE,
                        style=ft.ButtonStyle(
                            color=self.COLOR_PRIMARY,
                            bgcolor=ft.Colors.TRANSPARENT,
                            side=ft.BorderSide(2, self.COLOR_PRIMARY),
                            padding=20,
                            shape=ft.RoundedRectangleBorder(radius=10),
                        ),
                        on_click=lambda _: self.file_picker.pick_files(allow_multiple=False, allowed_extensions=["assm-save"])
                    )
                ],
                alignment=ft.MainAxisAlignment.CENTER,
                horizontal_alignment=ft.CrossAxisAlignment.CENTER,
            ),
            alignment=ft.alignment.center,
            expand=True
        )
        
        self.page.add(self.welcome_view)

    def on_file_picked(self, e: ft.FilePickerResultEvent):
        if not e.files:
            return
        
        filepath = e.files[0].path
        if not filepath:
            self.show_error("В веб-версии нельзя получить путь к файлу. Используйте Desktop или Android.")
            return

        ext = Path(filepath).suffix
        
        if ext == ".assm-save":
            self.load_session(filepath)
        else:
            self.load_excel(filepath)

    def load_excel(self, filepath):
        try:
            processor = ExcelProcessor(filepath)
            items_to_collect, self.shipment_info = processor.process_file()
            
            self.assembly_items = [
                {
                    **item,
                    'status': 'pending',
                    'collected_quantity': 0,
                    'box': 0
                } for item in items_to_collect
            ]
            
            self.input_file_path = filepath
            # Set default output directory to same folder as input file
            self.output_directory = str(Path(filepath).parent)
            self.current_item_index = 0
            self.current_box = 1
            
            self.start_assembly()
            
        except Exception as ex:
            self.show_error(f"Ошибка чтения файла: {ex}")

    def load_session(self, filepath):
        try:
            with open(filepath, "rb") as f:
                session_data = pickle.load(f)
            
            self.assembly_items = session_data["assembly_items"]
            self.current_item_index = session_data["current_item_index"]
            self.current_box = session_data["current_box"]
            self.shipment_info = session_data["shipment_info"]
            self.input_file_path = session_data["input_file_path"]
            
            self.start_assembly()
            
        except Exception as ex:
            self.show_error(f"Ошибка загрузки сессии: {ex}")

    def start_assembly(self):
        self.page.clean()
        self.is_review_mode = False
        self.build_assembly_ui()
        self.update_item_display()

    def build_assembly_ui(self):
        # --- Top Bar ---
        self.progress_text = ft.Text("Позиция 0 из 0", size=14, color=self.COLOR_TEXT_SEC)
        self.box_text = ft.Text("Коробка №1", size=18, weight=ft.FontWeight.BOLD, color=self.COLOR_PRIMARY)
        
        top_bar = ft.Container(
            content=ft.Row(
                [
                    self.box_text,
                    self.progress_text
                ],
                alignment=ft.MainAxisAlignment.SPACE_BETWEEN
            ),
            padding=ft.padding.symmetric(horizontal=20, vertical=10),
            bgcolor=self.COLOR_SURFACE
        )

        # --- Main Card ---
        self.location_text = ft.Text("-", size=60, weight=ft.FontWeight.BOLD, color=self.COLOR_ACCENT)
        self.name_text = ft.Text("-", size=18, text_align=ft.TextAlign.CENTER, color=self.COLOR_TEXT)
        self.barcode_text = ft.Text("-", size=60, weight=ft.FontWeight.BOLD, color=self.COLOR_PRIMARY)
        self.quantity_text = ft.Text("-", size=60, weight=ft.FontWeight.BOLD, color=self.COLOR_SUCCESS)
        
        main_card = ft.Container(
            content=ft.Column(
                [
                    self.name_text,
                    ft.Divider(color=self.COLOR_BG),
                    ft.Text("ЯЧЕЙКА", size=12, color=self.COLOR_TEXT_SEC),
                    self.location_text,
                    ft.Container(height=10),
                    ft.Text("ШТРИХКОД", size=12, color=self.COLOR_TEXT_SEC),
                    self.barcode_text,
                    ft.Container(height=10),
                    ft.Text("КОЛИЧЕСТВО", size=12, color=self.COLOR_TEXT_SEC),
                    self.quantity_text,
                ],
                horizontal_alignment=ft.CrossAxisAlignment.CENTER,
                scroll=ft.ScrollMode.AUTO
            ),
            bgcolor=self.COLOR_SURFACE,
            border_radius=20,
            padding=20,
            expand=True,
            margin=20,
            alignment=ft.alignment.center
        )

        # --- Bottom Actions ---
        self.collect_btn = ft.ElevatedButton(
            "СОБРАНО",
            style=ft.ButtonStyle(
                color=self.COLOR_BG,
                bgcolor=self.COLOR_SUCCESS,
                shape=ft.RoundedRectangleBorder(radius=15),
                padding=20,
            ),
            width=300,
            height=80,
            content=ft.Text("СОБРАНО", size=24, weight=ft.FontWeight.BOLD),
            on_click=self.on_collect
        )
        
        self.menu_btn = ft.IconButton(
            icon=ft.Icons.MENU,
            icon_size=30,
            icon_color=self.COLOR_TEXT,
            on_click=self.open_bottom_sheet
        )

        bottom_bar = ft.Container(
            content=ft.Column(
                [
                    self.collect_btn,
                    ft.Container(height=10),
                    ft.Row(
                        [
                            ft.IconButton(icon=ft.Icons.SAVE, icon_color=self.COLOR_TEXT_SEC, on_click=lambda _: self.save_file_picker.save_file(file_name="session.assm-save")),
                            self.menu_btn,
                            ft.IconButton(icon=ft.Icons.LIST, icon_color=self.COLOR_TEXT_SEC, on_click=lambda _: self.build_review_ui()),
                        ],
                        alignment=ft.MainAxisAlignment.SPACE_EVENLY
                    )
                ],
                horizontal_alignment=ft.CrossAxisAlignment.CENTER
            ),
            padding=20,
            bgcolor=self.COLOR_SURFACE,
            border_radius=ft.border_radius.only(top_left=20, top_right=20)
        )

        self.page.add(
            ft.Column(
                [
                    top_bar,
                    main_card,
                    bottom_bar
                ],
                expand=True,
                spacing=0
            )
        )
        
        # --- Bottom Sheet ---
        self.bs = ft.BottomSheet(
            ft.Container(
                ft.Column(
                    [
                        ft.ListTile(leading=ft.Icon(ft.Icons.SKIP_NEXT, color=ft.Colors.RED), title=ft.Text("Нет товара (Пропустить)"), on_click=self.on_skip),
                        ft.ListTile(leading=ft.Icon(ft.Icons.EDIT, color=ft.Colors.ORANGE), title=ft.Text("Изменить количество"), on_click=self.on_change_qty),
                        ft.ListTile(leading=ft.Icon(ft.Icons.INVENTORY, color=self.COLOR_PRIMARY), title=ft.Text("Следующая коробка"), on_click=self.on_next_box),
                        ft.ListTile(leading=ft.Icon(ft.Icons.FOLDER, color=ft.Colors.YELLOW), title=ft.Text("Выбрать папку сохранения"), on_click=self.on_select_folder),
                    ],
                    tight=True
                ),
                padding=10,
                bgcolor=self.COLOR_SURFACE
            ),
            open=False,
            on_dismiss=lambda _: print("Dismissed")
        )
        self.page.overlay.append(self.bs)

    def update_item_display(self):
        if 0 <= self.current_item_index < len(self.assembly_items):
            item = self.assembly_items[self.current_item_index]
            
            self.location_text.value = item.get('location', '---')
            self.name_text.value = item['name']
            
            barcode = item.get('barcode', '')
            # Show last 4 digits, remove leading dots if any
            display_barcode = barcode[-4:] if len(barcode) >= 4 else barcode
            self.barcode_text.value = display_barcode.lstrip('.')
            
            self.quantity_text.value = str(item['quantity'])
            
            self.progress_text.value = f"{self.current_item_index + 1} / {len(self.assembly_items)}"
            self.box_text.value = f"Коробка №{self.current_box}"
            
            self.page.update()
        else:
            self.finish_assembly()

    def next_item(self):
        self.current_item_index += 1
        while 0 <= self.current_item_index < len(self.assembly_items) and self.assembly_items[self.current_item_index]['status'] != 'pending':
            self.current_item_index += 1
        self.update_item_display()

    def on_collect(self, e):
        if self.current_item_index >= len(self.assembly_items): return
        
        item = self.assembly_items[self.current_item_index]
        item['status'] = 'collected'
        item['collected_quantity'] = item['quantity']
        item['box'] = self.current_box
        
        self.next_item()

    def open_bottom_sheet(self, e):
        self.bs.open = True
        self.bs.update()

    def on_skip(self, e):
        self.bs.open = False
        self.bs.update()
        
        if self.current_item_index >= len(self.assembly_items): return
        item = self.assembly_items[self.current_item_index]
        item['status'] = 'skipped'
        item['collected_quantity'] = 0
        item['box'] = 0
        self.next_item()

    def on_change_qty(self, e, item_index=None):
        self.bs.open = False
        self.bs.update()
        
        idx = item_index if item_index is not None else self.current_item_index
        if idx < 0 or idx >= len(self.assembly_items): return
        
        item = self.assembly_items[idx]
        
        def close_dlg(e):
            self.page.close(self.qty_dialog)

        def save_qty(e):
            try:
                new_qty = int(qty_field.value)
                new_box = int(box_field.value)
                if new_qty < 0 or new_box < 1: raise ValueError
                
                item['status'] = 'quantity_changed'
                item['collected_quantity'] = new_qty
                item['box'] = new_box
                
                # Если меняем текущий товар, обновляем и текущую коробку (опционально, но логично)
                if idx == self.current_item_index:
                    self.current_box = new_box
                
                self.page.close(self.qty_dialog)
                
                # Если мы в режиме обзора, обновляем таблицу
                if getattr(self, 'is_review_mode', False):
                    self.build_review_ui()
                else:
                    self.update_item_display()
                    # Если меняли текущий элемент и это не обзор, переходим к следующему
                    if idx == self.current_item_index:
                        self.next_item()

            except ValueError:
                qty_field.error_text = "Введите корректное число"
                qty_field.update()

        qty_field = ft.TextField(label="Новое количество", value=str(item.get('collected_quantity', item['quantity'])), autofocus=True, keyboard_type=ft.KeyboardType.NUMBER)
        box_field = ft.TextField(label="Номер коробки", value=str(item.get('box', self.current_box)), keyboard_type=ft.KeyboardType.NUMBER)

        self.qty_dialog = ft.AlertDialog(
            title=ft.Text(f"Изменить: {item['name']}"),
            content=ft.Column([qty_field, box_field], tight=True),
            actions=[
                ft.TextButton("Отмена", on_click=close_dlg),
                ft.TextButton("Сохранить", on_click=save_qty),
            ],
            actions_alignment=ft.MainAxisAlignment.END,
        )
        self.page.open(self.qty_dialog)

    def build_review_ui(self):
        self.page.clean()
        self.is_review_mode = True
        
        # --- Header ---
        header = ft.Container(
            content=ft.Row(
                [
                    ft.IconButton(ft.Icons.ARROW_BACK, on_click=lambda _: self.start_assembly()),
                    ft.Text("Обзор сборки", size=20, weight=ft.FontWeight.BOLD),
                    ft.Container() # Spacer
                ],
                alignment=ft.MainAxisAlignment.SPACE_BETWEEN
            ),
            padding=10,
            bgcolor=self.COLOR_SURFACE
        )

        # --- Data Table ---
        rows = []
        for i, item in enumerate(self.assembly_items):
            status_color = ft.Colors.WHITE
            status_icon = ft.Icons.CIRCLE_OUTLINED
            
            if item['status'] == 'collected':
                status_color = self.COLOR_SUCCESS
                status_icon = ft.Icons.CHECK_CIRCLE
            elif item['status'] == 'skipped':
                status_color = ft.Colors.RED
                status_icon = ft.Icons.CANCEL
            elif item['status'] == 'quantity_changed':
                status_color = ft.Colors.ORANGE
                status_icon = ft.Icons.EDIT
            
            box_val = str(item.get('box', '-'))
            if box_val == '0': box_val = '-'

            rows.append(
                ft.DataRow(
                    cells=[
                        ft.DataCell(ft.Icon(status_icon, color=status_color)),
                        # ft.DataCell(ft.Text(item['name'], width=150, no_wrap=True, overflow=ft.TextOverflow.ELLIPSIS)), # Removed as requested
                        ft.DataCell(ft.Text(item.get('location', '-'))),
                        ft.DataCell(ft.Text(str(item['quantity']))),
                        ft.DataCell(ft.Text(str(item['collected_quantity']), color=status_color, weight=ft.FontWeight.BOLD)),
                        ft.DataCell(ft.Text(box_val)),
                        ft.DataCell(ft.IconButton(ft.Icons.EDIT, icon_color=ft.Colors.GREY, on_click=lambda e, idx=i: self.on_change_qty(e, idx))),
                    ],
                )
            )

        self.review_table = ft.DataTable(
            columns=[
                ft.DataColumn(ft.Text("Статус")),
                # ft.DataColumn(ft.Text("Товар")), # Removed
                ft.DataColumn(ft.Text("Ячейка")),
                ft.DataColumn(ft.Text("План")),
                ft.DataColumn(ft.Text("Факт")),
                ft.DataColumn(ft.Text("Коробка")),
                ft.DataColumn(ft.Text("Править")),
            ],
            rows=rows,
            column_spacing=10,
            data_row_min_height=50,
        )

        self.page.add(
            ft.Column(
                [
                    header,
                    ft.Container(
                        content=ft.Column([self.review_table], scroll=ft.ScrollMode.AUTO),
                        expand=True,
                    )
                ],
                expand=True
            )
        )

    def on_next_box(self, e):
        self.bs.open = False
        self.bs.update()
        self.current_box += 1
        self.update_item_display()
        self.page.snack_bar = ft.SnackBar(ft.Text(f"Начата коробка №{self.current_box}"))
        self.page.snack_bar.open = True
        self.page.update()

    def on_select_folder(self, e):
        self.bs.open = False
        self.bs.update()
        self.folder_picker.get_directory_path()

    def on_folder_picked(self, e: ft.FilePickerResultEvent):
        if e.path:
            self.output_directory = e.path
            self.page.snack_bar = ft.SnackBar(ft.Text(f"Папка сохранения: {e.path}"))
            self.page.snack_bar.open = True
            self.page.update()

    def on_save_file_picked(self, e: ft.FilePickerResultEvent):
        if e.path:
            try:
                session_data = {
                    "assembly_items": self.assembly_items,
                    "current_item_index": self.current_item_index,
                    "current_box": self.current_box,
                    "shipment_info": self.shipment_info,
                    "input_file_path": self.input_file_path,
                }
                with open(e.path, "wb") as f:
                    pickle.dump(session_data, f)
                self.page.snack_bar = ft.SnackBar(ft.Text("Сессия сохранена!"))
                self.page.snack_bar.open = True
                self.page.update()
            except Exception as ex:
                self.show_error(f"Ошибка сохранения: {ex}")

    def finish_assembly(self):
        collected_data = [
            {
                'box': item['box'],
                'article': item['article'],
                'name': item['name'],
                'quantity': item['collected_quantity'],
                'barcode': item.get('barcode', '')
            }
            for item in self.assembly_items if item['status'] in ['collected', 'quantity_changed'] and item['collected_quantity'] > 0
        ]

        discrepancies = []
        for item in self.assembly_items:
            identifier = item.get('barcode') or f"Арт: {item['article']}"
            if item['status'] == 'skipped':
                discrepancies.append(f"Пропущено: {identifier} - {item['quantity']} шт.")
            elif item['status'] == 'quantity_changed' and item['collected_quantity'] != item['quantity']:
                 discrepancies.append(f"Изменено: {identifier} было {item['quantity']}, стало {item['collected_quantity']}")

        if not collected_data and not discrepancies:
            self.show_error("Нет данных для сохранения")
            return

        try:
            writer = ExcelWriter(
                collected_data=collected_data,
                shipment_info=self.shipment_info,
                discrepancies=discrepancies,
                original_file_path=self.input_file_path,
                output_directory=self.output_directory
            )
            output_filename = writer.generate_final_file()
            
            self.page.clean()
            
            content = [
                ft.Icon(ft.Icons.CHECK_CIRCLE, size=100, color=self.COLOR_SUCCESS),
                ft.Text("Сборка завершена!", size=30, weight=ft.FontWeight.BOLD, color=self.COLOR_TEXT),
                ft.Text(f"Файл сохранен:\n{Path(output_filename).absolute()}", size=16, color=self.COLOR_TEXT_SEC, text_align=ft.TextAlign.CENTER),
                ft.Container(height=20),
                ft.ElevatedButton("В главное меню", on_click=lambda _: self.init_ui())
            ]
            
            if discrepancies:
                content.append(ft.Container(height=20))
                content.append(ft.Text("Расхождения:", size=18, color=ft.Colors.RED))
                for d in discrepancies:
                    content.append(ft.Text(d, color=self.COLOR_TEXT_SEC, size=12))

            self.page.add(
                ft.Container(
                    content=ft.Column(
                        content,
                        alignment=ft.MainAxisAlignment.CENTER,
                        horizontal_alignment=ft.CrossAxisAlignment.CENTER,
                        scroll=ft.ScrollMode.AUTO
                    ),
                    alignment=ft.alignment.center,
                    expand=True,
                    padding=20
                )
            )

        except Exception as e:
            self.show_error(f"Ошибка сохранения: {e}")

    def show_error(self, message):
        self.page.snack_bar = ft.SnackBar(ft.Text(message, color=ft.Colors.WHITE), bgcolor=ft.Colors.RED)
        self.page.snack_bar.open = True
        self.page.update()

    def show_not_implemented(self, feature):
        self.page.snack_bar = ft.SnackBar(ft.Text(f"{feature} еще не реализовано"))
        self.page.snack_bar.open = True
        self.page.update()

def main(page: ft.Page):
    app = AssemblyApp(page)

if __name__ == "__main__":
    ft.app(target=main)
