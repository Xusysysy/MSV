# MSV 乐谱查看器 v2.0

## 新增功能

- **面部识别翻谱**：MediaPipe FaceLandmarker 实时识别面部动作，支持 Wink（眨眼）和撅嘴手势翻页
- **四方向手势控制**：左/右 Wink 控制前翻/后翻，左/右撅嘴控制前翻/后翻，可独立选择触发模式
- **配置浮层**：长按顶栏眼睛按钮打开设置面板，实时预览摄像头画面+面部关键点连线
- **关键点可视化**：预览画面叠加面部轮廓、眼睛、眉毛、嘴唇四组关键点连线，线条加粗至 3dp
- **参数持久化**：所有面部参数（镜像、阈值、偏置）自动保存到 DataStore，冷启动恢复
- **动作分数高亮**：翻谱阈值触发时顶栏眼睛按钮变红，分数下降后自动还原

## 面部识别架构

- **YUV 直接转 ARGB_8888**：绕过 JPEG 压缩路径，避免格式兼容性问题导致 native crash
- **模型延迟加载**：仅在开启识别时加载 face_landmarker.task，关闭时释放资源
- **单相机源**：FaceCamera 内嵌 Overlay 面板预览区，始终只有一个 CameraX 绑定，消除竞争闪退
- **alpha 渐显**：PreviewView 首帧延迟渲染，消除冷启动黑色矩形闪现
- **诊断日志**：完整记录模型加载、帧处理、手势检测全流程，支持 logcat 实时查看+本地文件导出

## 翻页动画统一

- **面部翻页共享 spring 动画**：面部手势触发的翻页使用与点击/滑动相同的弹簧动画（dampingRatio=0.75f, stiffness=280f）
- **打断机制**：连续面部手势自动 cancel 前一次动画并重新触发，与手动翻页行为一致
- **pendingFlip 架构**：面部手势设置 pendingFlip 状态 → Stage 检测后调用 doFlip → 动画完成回调 FlipDone

## 修复

- 修复面部识别开启即闪退问题（相机绑定竞争、模型加载时序）
- 修复冷启动黑色矩形闪现（FaceCamera 无条件运行导致）
- 修复关闭设置面板闪现（CameraX unbindAll 竞态）
- 修复面部翻页双重翻页 bug（doFlip 和 flipDone 重复调用 goToPage）
- 修复 YUV 转换中 0xFFFFFFFE 字面量 Long 溢出错误
- 修复 logcat 日志被覆盖问题（FaceLog 改为文件追加模式）

## 技术变更

- 移除 Cam/PH 死代码，Overlay 仅保留设置面板
- 移除 FaceRecognitionOverlay 无条件挂载问题（改为条件渲染）
- 移除 ViewerViewModel 中 faceManager.stateFlow 双写 faceEnabled
- 版本号：versionCode=7, versionName=2.0
