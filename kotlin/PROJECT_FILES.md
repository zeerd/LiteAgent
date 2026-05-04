# 项目文件列表及说明

本文档列出了 `TextAdventure` 项目的主要文件及其作用说明。

## 核心应用
- `app/src/main/java/com/liteagent/textadventure/TextAdventureApp.kt`: 应用程序类，负责初始化 Hilt 和加载 LiteRT-LM JNI 库。
- `app/src/main/java/com/liteagent/textadventure/MainActivity.kt`: 主 Activity，应用程序的入口点，设置 Compose 内容和导航。

## 数据层 (Data Layer)
### 存储库 (Repositories)
- `app/src/main/java/com/liteagent/textadventure/data/repository/ConversationRepository.kt`: 管理对话数据的存储库，处理聊天消息的持久化。
- `app/src/main/java/com/liteagent/textadventure/data/repository/StoryHistoryRepository.kt`: 管理故事历史记录的存储库。
- `app/src/main/java/com/liteagent/textadventure/data/local/AppSettingsRepository.kt`: 应用程序设置存储库，管理模型路径、加速模式等设置。

### 数据库 (Database)
- `app/src/main/java/com/liteagent/textadventure/data/db/AppDatabase.kt`: Room 数据库定义。
- `app/src/main/java/com/liteagent/textadventure/data/db/ConversationDao.kt`: 对话数据的数据库访问对象 (DAO)。
- `app/src/main/java/com/liteagent/textadventure/data/db/ConversationEntity.kt`: 对话消息的数据库实体类。
- `app/src/main/java/com/liteagent/textadventure/data/db/StoryHistoryDao.kt`: 故事历史的数据库访问对象 (DAO)。
- `app/src/main/java/com/liteagent/textadventure/data/db/StoryHistoryEntity.kt`: 故事历史的数据库实体类。

### 本地数据源 (Local Data)
- `app/src/main/java/com/liteagent/textadventure/data/local/StorySettingsDataSource.kt`: 预定义故事背景设置的数据源。

## 服务层 (Service Layer)
- `app/src/main/java/com/liteagent/textadventure/service/LiteRtLmService.kt`: 核心 AI 服务，封装了与 LiteRT-LM 模型的交互逻辑。
- `app/src/main/java/com/liteagent/textadventure/service/SkillManager.kt`: 技能管理器，处理 AI 技能的加载和执行。
- `app/src/main/java/com/liteagent/textadventure/service/ContextCompressor.kt`: 上下文压缩器，用于优化长对话的内存占用。

## 界面层 (UI Layer)
### 界面 (Screens)
- `app/src/main/java/com/liteagent/textadventure/ui/main/MainScreen.kt`: 游戏主界面，包含聊天窗口。
- `app/src/main/java/com/liteagent/textadventure/ui/main/WelcomeScreen.kt`: 欢迎界面。
- `app/src/main/java/com/liteagent/textadventure/ui/newstory/NewStoryScreen.kt`: 创建新故事界面。
- `app/src/main/java/com/liteagent/textadventure/ui/newstory/HistoryScreen.kt`: 历史故事列表界面。
- `app/src/main/java/com/liteagent/textadventure/ui/settings/SettingsScreen.kt`: 应用程序设置界面。

### 视图模型 (ViewModels)
- `app/src/main/java/com/liteagent/textadventure/ui/main/MainViewModel.kt`: 主界面的视图模型，处理游戏逻辑和消息流。
- `app/src/main/java/com/liteagent/textadventure/ui/newstory/NewStoryViewModel.kt`: 新故事界面的视图模型。
- `app/src/main/java/com/liteagent/textadventure/ui/settings/SettingsViewModel.kt`: 设置界面的视图模型。

### 组件 (Components)
- `app/src/main/java/com/liteagent/textadventure/ui/main/components/ChatInputBar.kt`: 聊天输入栏。
- `app/src/main/java/com/liteagent/textadventure/ui/main/components/ChatMessageItem.kt`: 聊天消息气泡。
- `app/src/main/java/com/liteagent/textadventure/ui/main/components/QuickActionButtons.kt`: 快捷动作按钮（如下一步、描述环境等）。

### 主题 (Theme)
- `app/src/main/java/com/liteagent/textadventure/ui/theme/Theme.kt`, `Color.kt`, `Type.kt`: 定义 Compose 主题、颜色方案和字体样式。

## 导航与依赖注入
- `app/src/main/java/com/liteagent/textadventure/navigation/NavDestinations.kt`: 导航路由定义。
- `app/src/main/java/com/liteagent/textadventure/navigation/AppNavHost.kt`: 导航图配置。
- `app/src/main/java/com/liteagent/textadventure/di/AppModule.kt`: Hilt 依赖注入模块。

## 模型 (Model)
- `app/src/main/java/com/liteagent/textadventure/model/ChatMessage.kt`: 定义聊天消息、角色、故事设置等基础数据模型。
