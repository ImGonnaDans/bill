# 记账本

一个基于 Android 的智能记账应用，支持自动监听支付通知并快速记账。个人vibe coding瞎玩使用hhh

## 功能特点

### 📱 基础记账
- 添加/修改/删除账单记录
- 支持支出和收入两种类型
- 自定义类别管理（支出/收入分类）
- 按日期分组查看账单
- 每月收支汇总

### 📊 数据统计
- 按年/月/自定义时间段统计
- 支出/收入/结余概览
- 折线图展示消费趋势
- 饼图展示分类占比
- 分类明细列表

### 🤖 自动记账
- 监听支付通知，自动识别金额
- 支持App：支付宝、微信、美团、抖音、京东、淘宝、拼多多
- 检测到账单后弹出通知提醒，点击即可预填金额
- 可自定义延迟时间（1-30秒）
- 支持保存并继续（连续记账）

### 💾 数据管理
- 导出为 Excel (.xlsx) 格式
- 从 Excel 导入备份数据
- 一键清空所有数据

## 系统要求

- Android 14+ (API 36)
- 已安装目标支付App

## 使用步骤

### 1. 安装后首次使用

打开应用 → 设置 → 自动记账设置：

### 2. 开启必要权限

**通知监听权限**：
- 点击"开启通知监听权限"
- 在系统设置中找到本应用并开启

**忽略电池优化**（必选，否则监听不到账单类通知哈）：
- 点击"开启忽略电池优化"
- 在弹出的窗口中选择"允许"

### 3. 开始使用

- 在任意支持的App中完成支付
- 通知栏会自动弹出"发现xx支出：¥XX.XX"的通知
- 点击通知即可打开添加账单窗口，金额已自动填好
- 选择类别后点击保存

### 4. 连续记账

在添加账单窗口中：
- **保存并退出**：保存后关闭窗口
- **保存并继续**：保存后不清除表单，可继续添加下一笔

## 项目结构

```
app/src/main/java/com/example/bill/
├── MainActivity.kt                 # 主Activity
├── data/
│   ├── AppDatabase.kt              # Room 数据库
│   ├── Bill.kt                     # 账单实体类
│   ├── BillDao.kt                  # 账单 DAO
│   ├── BillRepository.kt           # 账单仓库
│   ├── CategoryDao.kt              # 类别 DAO
│   ├── CategoryEntity.kt           # 类别实体类
│   ├── ExcelManager.kt             # Excel 导入导出
│   └── BillType.kt                 # 账单类型枚举
├── service/
│   ├── NotificationMonitorService.kt  # 通知监听服务
│   ├── AutoAddBillActivity.kt         # 自动记账 Activity
│   ├── AppNotificationParser.kt       # 通知解析器(7个App)
│   └── ParsedNotification.kt          # 解析结果数据类
├── ui/
│   ├── BillApp.kt                  # Compose 导航
│   ├── components/
│   │   └── AddBillDialog.kt        # 添加账单对话框
│   ├── pages/
│   │   ├── BillPage.kt             # 账单列表页
│   │   ├── StatsPage.kt            # 统计页
│   │   └── SettingsPage.kt         # 设置页
│   ├── theme/                      # 主题
│   └── viewmodel/
│       └── BillViewModel.kt        # ViewModel
```

## 技术栈

- **语言**：Kotlin
- **UI框架**：Jetpack Compose + Material 3
- **数据库**：Room (SQLite)
- **架构**：MVVM (ViewModel + Flow)
- **图表**：自定义 Compose Canvas 绘制
- **Excel**：Apache POI

## 构建

```bash
./gradlew assembleDebug
```

## 许可证

MIT License