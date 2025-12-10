
import flet as ft
from excel_processor import ExcelProcessor, ExcelWriter
import pickle
from pathlib import Path
import os

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
        self.output_directory = ""  # Will be set by user selection
        self.autosave_path = Path.home() / ".offline_assembler_autosave.assm-save"
        self.output_file_path = ""  # Store the final output file path for sharing

        # --- UI Components ---
        self.file_picker = ft.FilePicker(on_result=self.on_file_picked)
        self.page.overlay.append(self.file_picker)
        
        self.save_file_picker = ft.FilePicker(on_result=self.on_save_file_picked)
        self.page.overlay.append(self.save_file_picker)
        
        self.folder_picker = ft.FilePicker(on_result=self.on_folder_picked)
        self.page.overlay.append(self.folder_picker)

        # Try to load autosave on startup
        self.try_load_autosave()
        
        if not self.assembly_items:
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
            self.current_item_index = 0
            self.current_box = 1
            
            # Show folder selection dialog before starting assembly
            self.show_folder_selection_dialog()
            
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
            self.output_directory = session_data.get("output_directory", "")
            
            # If no output directory, ask for it
            if not self.output_directory:
                self.show_folder_selection_dialog()
            else:
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
        self.progress_text = ft.Text(
            "Позиция 0 из 0", 
            size=16,  # Increased from 14
            weight=ft.FontWeight.BOLD,  # Made bold
            color=self.COLOR_PRIMARY  # Changed from TEXT_SEC to PRIMARY
        )
        self.box_text = ft.Text("Коробка №1", size=18, weight=ft.FontWeight.BOLD, color=self.COLOR_PRIMARY)
        
        # Menu button for additional options
        self.top_menu_btn = ft.IconButton(
            icon=ft.Icons.MORE_VERT,
            icon_size=28,
            icon_color=self.COLOR_TEXT,
            on_click=self.open_top_menu
        )
        
        top_bar = ft.Container(
            content=ft.Row(
                [
                    self.box_text,
                    ft.Row(
                        [
                            self.progress_text,
                            self.top_menu_btn
                        ],
                        spacing=5
                    )
                ],
                alignment=ft.MainAxisAlignment.SPACE_BETWEEN
            ),
            padding=ft.padding.only(left=20, right=20, top=50, bottom=10),  # Extra top padding for mobile status bar
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
        
        # --- Top Menu (for additional options) ---
        self.top_menu_bs = ft.BottomSheet(
            ft.Container(
                ft.Column(
                    [
                        ft.ListTile(
                            leading=ft.Icon(ft.Icons.SAVE_ALT, color=self.COLOR_PRIMARY), 
                            title=ft.Text("Сгенерировать промежуточный файл"), 
                            on_click=self.on_generate_intermediate
                        ),
                        ft.ListTile(
                            leading=ft.Icon(ft.Icons.CHECK_CIRCLE, color=ft.Colors.GREEN), 
                            title=ft.Text("Завершить сборку досрочно"), 
                            on_click=self.on_finish_early
                        ),
                    ],
                    tight=True
                ),
                padding=10,
                bgcolor=self.COLOR_SURFACE
            ),
            open=False,
        )
        self.page.overlay.append(self.top_menu_bs)

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
        
        self.autosave_session()
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
        self.autosave_session()
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
                        self.autosave_session()
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
            
            # Extract last 4 digits of barcode
            barcode = item.get('barcode', '')
            barcode_last4 = barcode[-4:] if len(barcode) >= 4 else barcode
            barcode_last4 = barcode_last4.lstrip('.')

            rows.append(
                ft.DataRow(
                    cells=[
                        ft.DataCell(ft.Icon(status_icon, color=status_color)),
                        ft.DataCell(ft.Text(item.get('location', '-'))),
                        ft.DataCell(ft.Text(barcode_last4)),  # New barcode column
                        ft.DataCell(ft.Text(str(item['quantity']))),
                        ft.DataCell(
                            ft.Text(str(item['collected_quantity']), color=status_color, weight=ft.FontWeight.BOLD),
                            on_tap=lambda e, idx=i: self.on_edit_quantity_only(idx)  # Click to edit quantity
                        ),
                        ft.DataCell(
                            ft.Text(box_val),
                            on_tap=lambda e, idx=i: self.on_edit_box_only(idx)  # Click to edit box
                        ),
                    ],
                )
            )

        self.review_table = ft.DataTable(
            columns=[
                ft.DataColumn(ft.Text("Статус")),
                ft.DataColumn(ft.Text("Ячейка")),
                ft.DataColumn(ft.Text("ШК")),  # New barcode column
                ft.DataColumn(ft.Text("План")),
                ft.DataColumn(ft.Text("Факт")),
                ft.DataColumn(ft.Text("Коробка")),
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
        self.autosave_session()
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
    
    def show_folder_selection_dialog(self):
        """Show dialog to select output folder before starting assembly"""
        def on_select_folder_dialog(e):
            self.page.close(folder_dialog)
            self.folder_picker.get_directory_path()
        
        def on_folder_selected_for_start(e: ft.FilePickerResultEvent):
            if e.path:
                self.output_directory = e.path
                self.start_assembly()
            else:
                # User cancelled, show error and go back to welcome screen
                self.show_error("Необходимо выбрать папку для сохранения файла")
                self.init_ui()
        
        # Temporarily replace the folder picker callback
        original_callback = self.folder_picker.on_result
        self.folder_picker.on_result = on_folder_selected_for_start
        
        folder_dialog = ft.AlertDialog(
            title=ft.Text("Выберите папку для сохранения"),
            content=ft.Text("Перед началом сборки необходимо выбрать папку, куда будет сохранен итоговый файл."),
            actions=[
                ft.TextButton("Выбрать папку", on_click=on_select_folder_dialog),
            ],
            actions_alignment=ft.MainAxisAlignment.END,
        )
        self.page.open(folder_dialog)
    
    def on_edit_quantity_only(self, item_index):
        """Edit only the quantity for an item"""
        if item_index < 0 or item_index >= len(self.assembly_items):
            return
        
        item = self.assembly_items[item_index]
        
        def close_dlg(e):
            self.page.close(qty_dialog)
        
        def save_qty(e):
            try:
                new_qty = int(qty_field.value)
                if new_qty < 0:
                    raise ValueError
                
                item['status'] = 'quantity_changed'
                item['collected_quantity'] = new_qty
                
                self.page.close(qty_dialog)
                self.build_review_ui()  # Refresh the review table
            
            except ValueError:
                qty_field.error_text = "Введите корректное число"
                qty_field.update()
        
        qty_field = ft.TextField(
            label="Количество",
            value=str(item.get('collected_quantity', item['quantity'])),
            autofocus=True,
            keyboard_type=ft.KeyboardType.NUMBER
        )
        
        qty_dialog = ft.AlertDialog(
            title=ft.Text(f"Изменить количество"),
            content=qty_field,
            actions=[
                ft.TextButton("Отмена", on_click=close_dlg),
                ft.TextButton("Сохранить", on_click=save_qty),
            ],
            actions_alignment=ft.MainAxisAlignment.END,
        )
        self.page.open(qty_dialog)
    
    def on_edit_box_only(self, item_index):
        """Edit only the box number for an item"""
        if item_index < 0 or item_index >= len(self.assembly_items):
            return
        
        item = self.assembly_items[item_index]
        
        def close_dlg(e):
            self.page.close(box_dialog)
        
        def save_box(e):
            try:
                new_box = int(box_field.value)
                if new_box < 1:
                    raise ValueError
                
                item['box'] = new_box
                
                self.page.close(box_dialog)
                self.build_review_ui()  # Refresh the review table
            
            except ValueError:
                box_field.error_text = "Введите корректное число (минимум 1)"
                box_field.update()
        
        box_field = ft.TextField(
            label="Номер коробки",
            value=str(item.get('box', self.current_box)),
            autofocus=True,
            keyboard_type=ft.KeyboardType.NUMBER
        )
        
        box_dialog = ft.AlertDialog(
            title=ft.Text(f"Изменить коробку"),
            content=box_field,
            actions=[
                ft.TextButton("Отмена", on_click=close_dlg),
                ft.TextButton("Сохранить", on_click=save_box),
            ],
            actions_alignment=ft.MainAxisAlignment.END,
        )
        self.page.open(box_dialog)

    def on_save_file_picked(self, e: ft.FilePickerResultEvent):
        if e.path:
            try:
                session_data = {
                    "assembly_items": self.assembly_items,
                    "current_item_index": self.current_item_index,
                    "current_box": self.current_box,
                    "shipment_info": self.shipment_info,
                    "input_file_path": self.input_file_path,
                    "output_directory": self.output_directory,
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
            self.output_file_path = str(Path(output_filename).absolute())  # Store for sharing
            
            # Delete autosave after successful completion
            self.delete_autosave()
            
            self.page.clean()
            
            content = [
                ft.Icon(ft.Icons.CHECK_CIRCLE, size=100, color=self.COLOR_SUCCESS),
                ft.Text("Сборка завершена!", size=30, weight=ft.FontWeight.BOLD, color=self.COLOR_TEXT),
                ft.Text(f"Файл сохранен:\n{self.output_file_path}", size=16, color=self.COLOR_TEXT_SEC, text_align=ft.TextAlign.CENTER),
                ft.Container(height=20),
                ft.ElevatedButton(
                    "Поделиться файлом",
                    icon=ft.Icons.SHARE,
                    style=ft.ButtonStyle(
                        color=self.COLOR_BG,
                        bgcolor=self.COLOR_SUCCESS,
                        padding=20,
                        shape=ft.RoundedRectangleBorder(radius=10),
                    ),
                    on_click=lambda _: self.share_file_native()
                ),
                ft.Container(height=10),
                ft.ElevatedButton(
                    "Показать расположение",
                    icon=ft.Icons.FOLDER_OPEN,
                    style=ft.ButtonStyle(
                        color=self.COLOR_PRIMARY,
                        bgcolor=ft.Colors.TRANSPARENT,
                        side=ft.BorderSide(2, self.COLOR_PRIMARY),
                        padding=20,
                        shape=ft.RoundedRectangleBorder(radius=10),
                    ),
                    on_click=lambda _: self.show_file_location()
                ),
                ft.Container(height=10),
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

    def share_file_native(self):
        """Share file using native share dialog (Android/iOS)"""
        if self.output_file_path:
            try:
                # Use Flet's native share functionality
                self.page.share(
                    title="Файл сборки готов",
                    text=f"Сборка завершена: {Path(self.output_file_path).name}",
                    files=[self.output_file_path]
                )
            except Exception as ex:
                # Fallback to showing file location if sharing fails
                self.show_error(f"Функция 'Поделиться' недоступна: {ex}")
                self.show_file_location()
        else:
            self.show_error("Файл не найден")
    
    def show_file_location(self):
        """Show file location and provide copy option"""
        if self.output_file_path:
            def close_dlg(e):
                self.page.close(share_dialog)
            
            def copy_path(e):
                self.page.set_clipboard(self.output_file_path)
                self.page.snack_bar = ft.SnackBar(ft.Text("Путь скопирован в буфер обмена"))
                self.page.snack_bar.open = True
                self.page.update()
                self.page.close(share_dialog)
            
            def open_folder(e):
                try:
                    folder_path = str(Path(self.output_file_path).parent)
                    # Try to open the folder in file manager
                    self.page.launch_url(f"file://{folder_path}")
                except Exception as ex:
                    self.show_error(f"Не удалось открыть папку: {ex}")
                self.page.close(share_dialog)
            
            share_dialog = ft.AlertDialog(
                title=ft.Text("Файл сохранен"),
                content=ft.Column(
                    [
                        ft.Text("Путь к файлу:", weight=ft.FontWeight.BOLD),
                        ft.Text(self.output_file_path, size=12, selectable=True),
                    ],
                    tight=True,
                    spacing=10
                ),
                actions=[
                    ft.TextButton("Скопировать путь", on_click=copy_path),
                    ft.TextButton("Открыть папку", on_click=open_folder),
                    ft.TextButton("Закрыть", on_click=close_dlg),
                ],
                actions_alignment=ft.MainAxisAlignment.END,
            )
            self.page.open(share_dialog)
        else:
            self.show_error("Файл не найден")
    
    def show_error(self, message):
        self.page.snack_bar = ft.SnackBar(ft.Text(message, color=ft.Colors.WHITE), bgcolor=ft.Colors.RED)
        self.page.snack_bar.open = True
        self.page.update()

    def show_not_implemented(self, feature):
        self.page.snack_bar = ft.SnackBar(ft.Text(f"{feature} еще не реализовано"))
        self.page.snack_bar.open = True
        self.page.update()
    
    def autosave_session(self):
        """Automatically save session to a hidden file"""
        try:
            session_data = {
                "assembly_items": self.assembly_items,
                "current_item_index": self.current_item_index,
                "current_box": self.current_box,
                "shipment_info": self.shipment_info,
                "input_file_path": self.input_file_path,
                "output_directory": self.output_directory,
            }
            with open(self.autosave_path, "wb") as f:
                pickle.dump(session_data, f)
        except Exception:
            pass  # Silent fail for autosave
    
    def try_load_autosave(self):
        """Try to load autosaved session on startup"""
        if self.autosave_path.exists():
            try:
                with open(self.autosave_path, "rb") as f:
                    session_data = pickle.load(f)
                
                self.assembly_items = session_data["assembly_items"]
                self.current_item_index = session_data["current_item_index"]
                self.current_box = session_data["current_box"]
                self.shipment_info = session_data["shipment_info"]
                self.input_file_path = session_data["input_file_path"]
                self.output_directory = session_data.get("output_directory", "")
                
                # Show dialog to ask if user wants to continue
                def continue_session(e):
                    self.page.close(resume_dialog)
                    if self.output_directory:
                        self.start_assembly()
                    else:
                        self.show_folder_selection_dialog()
                
                def start_new(e):
                    self.page.close(resume_dialog)
                    self.delete_autosave()
                    self.assembly_items = []
                    self.init_ui()
                
                resume_dialog = ft.AlertDialog(
                    title=ft.Text("Восстановить сборку?"),
                    content=ft.Text("Найдена незавершенная сборка. Продолжить?")
,
                    actions=[
                        ft.TextButton("Начать новую", on_click=start_new),
                        ft.TextButton("Продолжить", on_click=continue_session),
                    ],
                    actions_alignment=ft.MainAxisAlignment.END,
                )
                self.page.open(resume_dialog)
                
            except Exception:
                self.delete_autosave()
    
    def delete_autosave(self):
        """Delete autosave file"""
        try:
            if self.autosave_path.exists():
                self.autosave_path.unlink()
        except Exception:
            pass
    
    def open_top_menu(self, e):
        """Open top menu with additional options"""
        self.top_menu_bs.open = True
        self.top_menu_bs.update()
    
    def on_generate_intermediate(self, e):
        """Generate intermediate Excel file with current progress"""
        self.top_menu_bs.open = False
        self.top_menu_bs.update()
        
        def confirm_generate(e):
            self.page.close(confirm_dialog)
            self.generate_excel_file(mark_uncollected=True, finish=False)
        
        def cancel_generate(e):
            self.page.close(confirm_dialog)
        
        confirm_dialog = ft.AlertDialog(
            title=ft.Text("Сгенерировать промежуточный файл?"),
            content=ft.Text("Все необработанные позиции будут помечены как не собранные. Сборка продолжится."),
            actions=[
                ft.TextButton("Отмена", on_click=cancel_generate),
                ft.TextButton("Да, сгенерировать", on_click=confirm_generate),
            ],
            actions_alignment=ft.MainAxisAlignment.END,
        )
        self.page.open(confirm_dialog)
    
    def on_finish_early(self, e):
        """Finish assembly early"""
        self.top_menu_bs.open = False
        self.top_menu_bs.update()
        
        def confirm_finish(e):
            self.page.close(confirm_dialog)
            self.generate_excel_file(mark_uncollected=True, finish=True)
        
        def cancel_finish(e):
            self.page.close(confirm_dialog)
        
        confirm_dialog = ft.AlertDialog(
            title=ft.Text("Завершить сборку досрочно?"),
            content=ft.Text("Все необработанные позиции будут помечены как не собранные. Сборка завершится."),
            actions=[
                ft.TextButton("Отмена", on_click=cancel_finish),
                ft.TextButton("Да, завершить", on_click=confirm_finish),
            ],
            actions_alignment=ft.MainAxisAlignment.END,
        )
        self.page.open(confirm_dialog)
    
    def generate_excel_file(self, mark_uncollected=False, finish=False):
        """Generate Excel file with current state"""
        # Mark all pending items as skipped if requested
        if mark_uncollected:
            for item in self.assembly_items:
                if item['status'] == 'pending':
                    item['status'] = 'skipped'
                    item['collected_quantity'] = 0
                    item['box'] = 0
        
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
            self.output_file_path = str(Path(output_filename).absolute())
            
            if finish:
                # Delete autosave and go to completion screen
                self.delete_autosave()
                self.finish_assembly()
            else:
                # Show success message and continue
                self.page.snack_bar = ft.SnackBar(
                    ft.Text(f"Файл сохранен: {Path(output_filename).name}"),
                    bgcolor=self.COLOR_SUCCESS
                )
                self.page.snack_bar.open = True
                self.page.update()
                
                # Reset pending items back to pending if we marked them
                if mark_uncollected:
                    for item in self.assembly_items:
                        if item['status'] == 'skipped' and item['collected_quantity'] == 0:
                            item['status'] = 'pending'
        
        except Exception as ex:
            self.show_error(f"Ошибка сохранения: {ex}")

def main(page: ft.Page):
    app = AssemblyApp(page)

if __name__ == "__main__":
    ft.app(target=main)
