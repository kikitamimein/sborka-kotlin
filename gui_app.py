
import tkinter as tk
from tkinter import filedialog, messagebox, simpledialog, ttk
import pickle
from excel_processor import ExcelProcessor, ExcelWriter

class AssemblyApp:
    def __init__(self, root):
        self.root = root
        self.root.title("Сборщик Заказов")
        self.root.geometry("500x650") # Увеличили размер окна под новые элементы
        self.root.resizable(False, False)

        # --- Стилизация (темная тема) ---
        dark_bg = "#2e2e2e"
        light_fg = "#fafafa"
        accent_color = "#00adb5"
        text_color = "#eeeeee"
        style = ttk.Style(self.root)
        style.theme_use('clam')

        self.root.configure(bg=dark_bg)

        style.configure("TFrame", background=dark_bg)
        
        style.configure(
            "TButton",
            background=accent_color,
            foreground=dark_bg,
            font=("Helvetica", 11, "bold"),
            padding=10,
            borderwidth=0
        )
        style.map(
            "TButton",
            background=[('active', '#00cdd5'), ('disabled', '#3a3a3a')],
            foreground=[('disabled', '#6e6e6e')]
        )
        
        style.configure(
            "Large.TButton",
            font=("Helvetica", 16, "bold"),
            padding=15
        )

        style.configure("Header.TLabel", font=("Helvetica", 14, "bold"), background=dark_bg, foreground=light_fg)
        style.configure("Info.TLabel", font=("Helvetica", 12), background=dark_bg, foreground=text_color, wraplength=450)
        
        # Новые стили для крупных элементов
        style.configure("Location.TLabel", font=("Helvetica", 24, "bold"), background=dark_bg, foreground="#ffcc00") # Желтый
        style.configure("Barcode.TLabel", font=("Helvetica", 20), background=dark_bg, foreground="#00adb5") # Циан
        style.configure("Quantity.TLabel", font=("Helvetica", 36, "bold"), background=dark_bg, foreground="#00ff00") # Зеленый
        style.configure("QuantityTitle.TLabel", font=("Helvetica", 12), background=dark_bg, foreground="#aaaaaa")
        
        style.configure("Progress.TLabel", font=("Helvetica", 10, "bold"), background=dark_bg, foreground=accent_color)

        # Стили для Treeview
        style.configure("Treeview", background="#3c3c3c", foreground=text_color, fieldbackground="#3c3c3c", rowheight=25)
        style.map("Treeview", background=[('selected', accent_color)])
        style.configure("Treeview.Heading", background="#2e2e2e", foreground=light_fg, font=("Helvetica", 10, "bold"))
        
        style.configure("TScrollbar", background=dark_bg, troughcolor="#3c3c3c", bordercolor=dark_bg, arrowcolor=accent_color)
        style.configure("TCombobox", background=accent_color, foreground=dark_bg, fieldbackground="#3c3c3c", selectbackground=accent_color, selectforeground=dark_bg)
        style.configure("TSpinbox", background="#3c3c3c", foreground=text_color, fieldbackground="#3c3c3c", buttonbackground=accent_color, selectbackground=accent_color, selectforeground=dark_bg)


        # Инициализация состояния
        self.assembly_items = []
        self.current_item_index = 0
        self.current_box = 1
        self.shipment_info = ""
        self.input_file_path = ""

        # --- UI Элементы ---
        self.main_frame = ttk.Frame(root, padding="20")
        self.main_frame.pack(fill=tk.BOTH, expand=True)
        
        # 1. Счетчик позиций
        self.progress_label = ttk.Label(self.main_frame, text="", style="Progress.TLabel", anchor="center")
        self.progress_label.pack(pady=(0, 10), fill=tk.X)

        # 2. Фрейм управления файлами
        self.file_ops_frame = ttk.Frame(self.main_frame)
        self.file_ops_frame.pack(fill=tk.X, pady=5)

        self.load_button = ttk.Button(self.file_ops_frame, text="Открыть Excel", command=self.load_file)
        self.load_session_button = ttk.Button(self.file_ops_frame, text="Загрузить сборку", command=self.load_session)
        
        self.review_button = ttk.Button(self.file_ops_frame, text="Обзор и правка", command=self.open_review_window)
        self.save_session_button = ttk.Button(self.file_ops_frame, text="Сохранить прогресс", command=self.save_session)

        # 3. Информация о товаре
        self.info_frame = ttk.Frame(self.main_frame)
        self.info_frame.pack(pady=10, fill=tk.BOTH, expand=True)

        # Наименование
        self.name_label = ttk.Label(self.info_frame, text="Загрузите файл...", style="Info.TLabel", anchor="center")
        self.name_label.pack(pady=(10, 5), fill=tk.X)
        
        # Ячейка
        self.location_label = ttk.Label(self.info_frame, text="", style="Location.TLabel", anchor="center")
        self.location_label.pack(pady=10, fill=tk.X)
        
        # Разделитель
        ttk.Separator(self.info_frame, orient='horizontal').pack(fill='x', pady=10)

        # Штрихкод
        self.barcode_label = ttk.Label(self.info_frame, text="", style="Barcode.TLabel", anchor="center")
        self.barcode_label.pack(pady=5, fill=tk.X)

        # Количество (с подписью)
        qty_frame = ttk.Frame(self.info_frame)
        qty_frame.pack(pady=15)
        
        ttk.Label(qty_frame, text="Количество:", style="QuantityTitle.TLabel").pack()
        self.quantity_label = ttk.Label(qty_frame, text="", style="Quantity.TLabel")
        self.quantity_label.pack()

        self.box_label = ttk.Label(self.main_frame, text="", style="Header.TLabel", anchor="center")
        self.box_label.pack(pady=10)

        # 4. Кнопки действий
        self.button_frame = ttk.Frame(self.main_frame)
        self.button_frame.pack(pady=10, fill=tk.X)
        
        self.collect_button = ttk.Button(self.button_frame, text="СОБРАНО", command=self.on_collect, style="Large.TButton")
        self.collect_button.pack(fill=tk.X, pady=(0, 10))
        
        self.actions_menubutton = ttk.Menubutton(self.button_frame, text="Действия")
        self.actions_menu = tk.Menu(self.actions_menubutton, tearoff=0)
        self.actions_menubutton.config(menu=self.actions_menu)
        
        self.actions_menu.add_command(label="Нету товара", command=self.on_skip)
        self.actions_menu.add_command(label="Изменить количество", command=self.on_change_quantity)
        self.actions_menu.add_separator()
        self.actions_menu.add_command(label="След. коробка", command=self.on_next_box)
        
        self.actions_menubutton.pack(fill=tk.X)

        self.update_ui_for_new_file()

    def update_button_states(self, is_active: bool):
        state = 'normal' if is_active else 'disabled'
        self.collect_button.config(state=state)
        self.actions_menubutton.config(state=state)

    def reset_state(self):
        self.assembly_items = []
        self.current_item_index = 0
        self.current_box = 1
        self.shipment_info = ""
        self.input_file_path = ""
        self.update_ui_for_new_file()

    def load_file(self):
        filepath = filedialog.askopenfilename(
            title="Выберите Excel файл",
            filetypes=(("Excel files", "*.xlsx *.xls"), ("All files", "*.*"))
        )
        if not filepath:
            return

        self.reset_state()
        self.input_file_path = filepath
        
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

            if not self.assembly_items:
                messagebox.showerror("Ошибка", "Не удалось найти товары в файле. Проверьте формат.")
                return

        except Exception as e:
            messagebox.showerror("Ошибка при чтении файла", f"Не удалось обработать файл:\n{e}")
            return
            
        self.update_ui_for_new_file()
        self.display_current_item()

    def display_current_item(self):
        if 0 <= self.current_item_index < len(self.assembly_items):
            item = self.assembly_items[self.current_item_index]
            
            name = item['name']
            location = item.get('location', '---')
            barcode = item.get('barcode', '')
            quantity = item['quantity']
            
            short_barcode = barcode[-4:] if len(barcode) >= 4 else barcode
            
            self.name_label.config(text=name)
            self.location_label.config(text=location)
            self.barcode_label.config(text=f"...{short_barcode}")
            self.quantity_label.config(text=f"{quantity} шт.")
            
            self.progress_label.config(text=f"Позиция {self.current_item_index + 1} из {len(self.assembly_items)}")
        else:
            self.finish_assembly()

    def update_ui_for_new_file(self):
        is_loaded = bool(self.assembly_items)
        self.update_button_states(is_loaded)
        
        if is_loaded:
            self.load_button.pack_forget()
            self.load_session_button.pack_forget()
            
            self.review_button.pack(side=tk.LEFT, expand=True, fill=tk.X, padx=(0, 5))
            self.save_session_button.pack(side=tk.LEFT, expand=True, fill=tk.X, padx=(5, 0))
            
            self.box_label.config(text=f"Коробка №{self.current_box}")
        else:
            self.review_button.pack_forget()
            self.save_session_button.pack_forget()
            
            self.load_button.pack(side=tk.LEFT, expand=True, fill=tk.X, padx=(0, 5))
            self.load_session_button.pack(side=tk.LEFT, expand=True, fill=tk.X, padx=(5, 0))
            
            self.name_label.config(text="Загрузите файл для начала сборки.")
            self.location_label.config(text="")
            self.barcode_label.config(text="")
            self.quantity_label.config(text="")
            self.progress_label.config(text="")
            self.box_label.config(text="")

    def next_item(self):
        self.current_item_index += 1
        while 0 <= self.current_item_index < len(self.assembly_items) and self.assembly_items[self.current_item_index]['status'] != 'pending':
            self.current_item_index += 1
        
        self.display_current_item()

    def on_collect(self):
        if self.current_item_index >= len(self.assembly_items): return
        
        item = self.assembly_items[self.current_item_index]
        item['status'] = 'collected'
        item['collected_quantity'] = item['quantity']
        item['box'] = self.current_box
        
        self.next_item()

    def on_skip(self):
        if self.current_item_index >= len(self.assembly_items): return

        item = self.assembly_items[self.current_item_index]
        item['status'] = 'skipped'
        item['collected_quantity'] = 0
        item['box'] = 0
        
        self.next_item()

    def on_change_quantity(self):
        if self.current_item_index >= len(self.assembly_items): return
        
        item = self.assembly_items[self.current_item_index]
        
        new_quantity = simpledialog.askinteger(
            "Изменить количество",
            f"Введите новое количество для:\n{item['name']}",
            initialvalue=item['quantity'],
            minvalue=0
        )
        
        if new_quantity is not None:
            item['status'] = 'quantity_changed'
            item['collected_quantity'] = new_quantity
            item['box'] = self.current_box
            self.next_item()

    def on_next_box(self):
        self.current_box += 1
        self.box_label.config(text=f"Коробка №{self.current_box}")
        messagebox.showinfo("Новая коробка", f"Начата сборка в коробку №{self.current_box}")

    def save_session(self):
        filepath = filedialog.asksaveasfilename(
            title="Сохранить прогресс сборки",
            defaultextension=".assm-save",
            filetypes=(("Файлы сборки", "*.assm-save"), ("All files", "*.*"))
        )
        if not filepath:
            return

        session_data = {
            "assembly_items": self.assembly_items,
            "current_item_index": self.current_item_index,
            "current_box": self.current_box,
            "shipment_info": self.shipment_info,
            "input_file_path": self.input_file_path,
        }
        
        try:
            with open(filepath, "wb") as f:
                pickle.dump(session_data, f)
            messagebox.showinfo("Успех", f"Прогресс сборки сохранен в файл:\n{filepath}")
        except Exception as e:
            messagebox.showerror("Ошибка сохранения", f"Не удалось сохранить сессию:\n{e}")

    def load_session(self):
        filepath = filedialog.askopenfilename(
            title="Загрузить прогресс сборки",
            filetypes=(("Файлы сборки", "*.assm-save"), ("All files", "*.*"))
        )
        if not filepath:
            return

        try:
            with open(filepath, "rb") as f:
                session_data = pickle.load(f)
            
            self.assembly_items = session_data["assembly_items"]
            self.current_item_index = session_data["current_item_index"]
            self.current_box = session_data["current_box"]
            self.shipment_info = session_data["shipment_info"]
            self.input_file_path = session_data["input_file_path"]
            
            self.update_ui_for_new_file()
            self.display_current_item()
            messagebox.showinfo("Успех", "Прогресс сборки успешно загружен.")

        except Exception as e:
            messagebox.showerror("Ошибка загрузки", f"Не удалось загрузить сессию:\n{e}")
            self.reset_state()
            
    def open_review_window(self):
        if not hasattr(self, 'review_window') or not self.review_window.winfo_exists():
            self.review_window = ReviewWindow(self)
        self.review_window.deiconify()

    def finish_assembly(self):
        if hasattr(self, 'review_window') and self.review_window.winfo_exists():
            self.review_window.destroy()

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
            messagebox.showwarning("Сборка пуста", "Нет данных для сохранения.")
            self.reset_state()
            return
            
        try:
            writer = ExcelWriter(
                collected_data=collected_data,
                shipment_info=self.shipment_info,
                discrepancies=discrepancies,
                original_file_path=self.input_file_path
            )
            output_filename = writer.generate_final_file()
            
            summary_message = f"Сборка завершена!\n\nФайл сохранен как:\n{output_filename}"
            if discrepancies:
                summary_message += "\n\nОбнаружены расхождения:\n" + "\n".join(discrepancies)
            
            messagebox.showinfo("Готово!", summary_message)
        
        except Exception as e:
            messagebox.showerror("Ошибка сохранения", f"Не удалось сохранить файл:\n{e}")
        
        finally:
            self.reset_state()


class ReviewWindow(tk.Toplevel):
    def __init__(self, master):
        super().__init__(master.root)
        self.master_app = master
        self.title("Обзор и правка сборки")
        self.geometry("800x600")

        style = ttk.Style()
        dark_bg = style.lookup("TFrame", "background")
        self.configure(bg=dark_bg)

        self.protocol("WM_DELETE_WINDOW", self.withdraw)

        self.create_widgets()
        self.populate_tree()

    def create_widgets(self):
        main_frame = ttk.Frame(self, padding="10")
        main_frame.pack(fill="both", expand=True)

        columns = ("#1", "#2", "#3", "#4", "#5", "#6")
        self.tree = ttk.Treeview(main_frame, columns=columns, show="headings")
        
        self.tree.heading("#1", text="Статус")
        self.tree.heading("#2", text="Артикул")
        self.tree.heading("#3", text="Наименование")
        self.tree.heading("#4", text="Требуется")
        self.tree.heading("#5", text="Собрано")
        self.tree.heading("#6", text="Коробка №")

        self.tree.column("#1", width=100, anchor="w")
        self.tree.column("#2", width=100, anchor="w")
        self.tree.column("#3", width=250, anchor="w")
        self.tree.column("#4", width=80, anchor="center")
        self.tree.column("#5", width=80, anchor="center")
        self.tree.column("#6", width=80, anchor="center")
        
        self.tree.pack(side="left", fill="both", expand=True)

        scrollbar = ttk.Scrollbar(main_frame, orient="vertical", command=self.tree.yview)
        self.tree.configure(yscrollcommand=scrollbar.set)
        scrollbar.pack(side="right", fill="y")

        button_frame = ttk.Frame(self, padding="10")
        button_frame.pack(fill="x")

        edit_button = ttk.Button(button_frame, text="Редактировать выбранное", command=self.edit_selected_item)
        edit_button.pack(side="left", padx=5)

        refresh_button = ttk.Button(button_frame, text="Обновить", command=self.populate_tree)
        refresh_button.pack(side="left", padx=5)
        
        close_button = ttk.Button(button_frame, text="Закрыть", command=self.withdraw)
        close_button.pack(side="right", padx=5)

    def populate_tree(self):
        selected_item = self.tree.focus()
        
        for i in self.tree.get_children():
            self.tree.delete(i)
        
        status_map = {
            'pending': 'В ожидании',
            'collected': 'Собрано',
            'skipped': 'Пропущено',
            'quantity_changed': 'Кол-во изменено'
        }

        for item in self.master_app.assembly_items:
            status = status_map.get(item['status'], 'Неизвестно')
            box = item['box'] if item['box'] > 0 else "-"
            values = (
                status,
                item['article'],
                item['name'],
                item['quantity'],
                item['collected_quantity'],
                box
            )
            self.tree.insert("", "end", values=values, iid=item['article'])
        
        if selected_item and self.tree.exists(selected_item):
            self.tree.focus(selected_item)
            self.tree.selection_set(selected_item)

    def edit_selected_item(self):
        selected_iid = self.tree.focus()
        if not selected_iid:
            messagebox.showwarning("Нет выбора", "Пожалуйста, выберите товар для редактирования.")
            return
        
        selected_item_data = None
        for item in self.master_app.assembly_items:
            if item['article'] == selected_iid:
                selected_item_data = item
                break
        
        if selected_item_data:
            dialog = EditItemDialog(self, "Редактировать позицию", selected_item_data)
            if dialog.result:
                self.master_app.assembly_items[self.master_app.assembly_items.index(selected_item_data)] = dialog.result
                self.populate_tree()
                self.master_app.display_current_item()


class EditItemDialog(simpledialog.Dialog):
    def __init__(self, parent, title, item_data):
        self.item = item_data.copy()
        super().__init__(parent, title)

    def body(self, master):
        self.result = None

        style = ttk.Style()
        dark_bg = style.lookup("TFrame", "background")
        master.configure(bg=dark_bg)

        ttk.Label(master, text="Статус:").grid(row=0, sticky="w")
        self.status_var = tk.StringVar(value=self.item['status'])
        statuses = ['pending', 'collected', 'skipped', 'quantity_changed']
        status_menu = ttk.Combobox(master, textvariable=self.status_var, values=statuses, state="readonly")
        status_menu.grid(row=0, column=1, sticky="ew")

        ttk.Label(master, text="Собрано:").grid(row=1, sticky="w")
        self.quantity_var = tk.IntVar(value=self.item['collected_quantity'])
        quantity_spinbox = ttk.Spinbox(master, from_=0, to=9999, textvariable=self.quantity_var)
        quantity_spinbox.grid(row=1, column=1, sticky="ew")

        ttk.Label(master, text="Коробка №:").grid(row=2, sticky="w")
        self.box_var = tk.IntVar(value=self.item['box'])
        box_spinbox = ttk.Spinbox(master, from_=0, to=999, textvariable=self.box_var)
        box_spinbox.grid(row=2, column=1, sticky="ew")

        return status_menu 

    def apply(self):
        self.item['status'] = self.status_var.get()
        self.item['collected_quantity'] = self.quantity_var.get()
        self.item['box'] = self.box_var.get()
        
        if self.item['status'] == 'skipped':
            self.item['collected_quantity'] = 0
            self.item['box'] = 0

        if self.item['status'] == 'collected':
             self.item['collected_quantity'] = self.item['quantity']

        self.result = self.item

if __name__ == "__main__":
    root = tk.Tk()
    app = AssemblyApp(root)
    root.mainloop()
