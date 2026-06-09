# Checklist

- [ ] 表格插入后渲染为带边框、表头加粗的 Compose Table 组件
- [ ] 表格单元格可点击进入编辑态, 失焦回写 markdown
- [ ] 新建表格走 `TableInsertDialog`, 行 × 列输入框工作正常
- [ ] 「左对齐 / 居中 / 右对齐」点击后, 当前光标所在段落整段对齐生效
- [ ] 已对齐段落点击其他对齐, 替换对齐不重复包绕
- [ ] 「B / I / U / S / 高亮 / 字号 / 字色」选区非空时正确包裹
- [ ] 选区为空时点击样式按钮弹 Toast「请先选中要修改的文字」, 不插入空标记
- [ ] 重复点击同一样式按钮可切换移除
- [ ] 选区非空时包绕后选区移到标记末尾, 便于连续编辑
- [ ] `BasicTextField` value/onValueChange 已升级为 `TextFieldValue`
- [ ] 撤销 / 重做栈在文本改动后仍可正常工作
- [ ] 普通段落 / 列表 / 待办渲染未受表格渲染逻辑影响
- [ ] `gradle :app:assembleDebug` 通过, 0 编译错误 0 warning
- [ ] `versionName = 1.0.3`, `versionCode = 3` 保持
