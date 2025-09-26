#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import io
import requests
import numpy as np
from PIL import Image, ImageTk, ImageFilter
import tkinter as tk
from tkinter import ttk, filedialog, messagebox

ESP32_URL = 'http://192.168.1.1/upload'

class EPDUploader(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title('EPD 400×300 上传工具')
        self.geometry('900x650')
        self.resizable(False, False)

        self.img_orig  = None
        self.bw_thr    = tk.IntVar(value=128)
        self.red_thr   = tk.IntVar(value=150)

        self.build_ui()
        self.update_preview()

    # -------------------- UI --------------------
    def build_ui(self):
        frm_left = ttk.Frame(self)
        frm_left.pack(side='left', padx=10, pady=10, fill='y')

        ttk.Button(frm_left, text="打开图片", command=self.open_image).pack(pady=5)
        ttk.Label(frm_left, text="旋转角度").pack()
        self.angle = tk.IntVar(value=0)
        ttk.Scale(frm_left, from_=0, to=360, orient='horizontal',
                  variable=self.angle, command=lambda _: self.update_preview()).pack()

        ttk.Label(frm_left, text="黑白阈值").pack()
        ttk.Scale(frm_left, from_=0, to=255, orient='horizontal',
                  variable=self.bw_thr, command=lambda _: self.update_preview()).pack()

        ttk.Label(frm_left, text="红色阈值").pack()
        ttk.Scale(frm_left, from_=0, to=255, orient='horizontal',
                  variable=self.red_thr, command=lambda _: self.update_preview()).pack()

        ttk.Button(frm_left, text="上传 ESP32", command=self.upload).pack(pady=20)

        # 仅保留“最终屏效果”预览
        self.lbl_final = ttk.Label(self)
        self.lbl_final.pack(side='right', padx=10, pady=10)
        ttk.Label(self, text='最终屏效果（黑/红/白）').pack(side='right')

    # -------------------- 打开图片 --------------------
    def open_image(self):
        path = filedialog.askopenfilename(title="选择图片",
                                        filetypes=[("Image files", "*.png;*.jpg;*.jpeg;*.bmp")])
        if path:
            self.img_orig = Image.open(path).convert('RGB')
            self.angle.set(0)
            self.update_preview()

    # -------------------- 留白缩放 --------------------
    def fit_with_padding(self, img, size=(400, 300), bg='white'):
        img = img.copy()
        img.thumbnail(size, Image.LANCZOS)
        bg = Image.new('RGB', size, bg)
        offset = ((size[0] - img.width) // 2, (size[1] - img.height) // 2)
        bg.paste(img, offset)
        return bg

    # -------------------- 预览 + 生成 bin --------------------
    def update_preview(self):
        if self.img_orig is None:
            return

        tmp = self.img_orig.rotate(self.angle.get(), expand=True)
        img_show = self.fit_with_padding(tmp, (400, 300), 'white')
        arr = np.array(img_show)

        # 灰度
        gray = np.dot(arr[...,:3], [0.299, 0.587, 0.114]).astype(np.uint8)

        # 1-bit 黑白（阈值）
        bw = (gray > self.bw_thr.get()).astype(np.uint8)

        # 1-bit 红色
        rd_mask = (arr[...,0] > self.red_thr.get()) & (arr[...,1] < 100) & (arr[...,2] < 100)
        rd = rd_mask.astype(np.uint8)

        # ESP32 bin：反相黑白 / 原样红
        self.bw_bytes = np.packbits(1 - bw, axis=-1).tobytes()
        self.rd_bytes = np.packbits(rd, axis=-1).tobytes()

        # 叠色预览：黑/红/白
        preview = np.zeros((300, 400, 3), dtype=np.uint8)
        preview[(bw == 0) & (rd == 0)] = [0, 0, 0]       # 黑
        preview[rd == 1]                = [255, 0, 0]    # 红
        preview[(bw == 1) & (rd == 0)]  = [255, 255, 255] # 白
        preview_pil = Image.fromarray(preview, 'RGB').resize((400 * 2, 300 * 2))

        self.lbl_final.image = ImageTk.PhotoImage(preview_pil)
        self.lbl_final.config(image=self.lbl_final.image)

    # -------------------- 上传 --------------------
    def upload(self):
        if self.img_orig is None:
            messagebox.showwarning("警告", "请先打开图片")
            return
        try:
            files = {'bw_image': ('bw_image.bin', io.BytesIO(self.bw_bytes), 'application/octet-stream')}
            r1 = requests.post(ESP32_URL, files=files, timeout=10)
            files = {'red_image': ('red_image.bin', io.BytesIO(self.rd_bytes), 'application/octet-stream')}
            r2 = requests.post(ESP32_URL, files=files, timeout=10)
            messagebox.showinfo("结果", f"上传完成\nBW: {r1.status_code}  {r1.text}\nRD: {r2.status_code}  {r2.text}")
        except Exception as e:
            messagebox.showerror("错误", str(e))

# -------------------- main --------------------
if __name__ == '__main__':
    EPDUploader().mainloop()