# 智能化在线教学支持服务平台（IOTP）API 接口文档

> **版本**: V1.0  
> **作者**: 杨雨洁、周梦灵、钟嶙怡  
> **最后更新**: 2026-06-18  
> **技术栈**: SpringBoot + MyBatisPlus + Vue3 + MySQL + Redis

---

## 目录

1. [接口规范说明](#1-接口规范说明)
2. [统一登录模块](#2-统一登录模块)
3. [学生端接口](#3-学生端接口)
4. [教师端接口](#4-教师端接口)
5. [管理员端接口](#5-管理员端接口)
6. [公共接口](#6-公共接口)
7. [错误码对照表](#7-错误码对照表)

---

## 1. 接口规范说明

### 1.1 基础信息

| 项目 | 说明 |
|------|------|
| 协议 | HTTP / HTTPS |
| 请求方式 | RESTful（GET / POST / PUT / DELETE） |
| 数据格式 | JSON |
| 字符编码 | UTF-8 |
| Base URL | `http://{host}:{port}/api/v1` |

### 1.2 通用请求头

```http
Content-Type: application/json
Authorization: Bearer {token}
```

> 除登录、忘记密码接口外，所有接口需携带 `Authorization` 请求头。

### 1.3 统一响应格式

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {},
  "timestamp": 1718697600000
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| code | Integer | 状态码，200 表示成功 |
| message | String | 提示信息 |
| data | Object/Array/null | 返回数据 |
| timestamp | Long | 响应时间戳（毫秒） |

### 1.4 分页响应格式

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "records": [],
    "total": 100,
    "page": 1,
    "pageSize": 10,
    "totalPages": 10
  },
  "timestamp": 1718697600000
}
```

### 1.5 通用查询参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | Integer | 否 | 页码，默认 1 |
| pageSize | Integer | 否 | 每页条数，默认 10，最大 100 |
| keyword | String | 否 | 模糊搜索关键词 |
| sortField | String | 否 | 排序字段 |
| sortOrder | String | 否 | 排序方向：asc / desc |

---

## 2. 统一登录模块

### 2.1 账号密码登录

**POST** `/auth/login`

**请求体：**

```json
{
  "account": "2024001",
  "password": "123456",
  "role": "STUDENT"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| account | String | 是 | 学号/工号/管理员账号 |
| password | String | 是 | 登录密码 |
| role | String | 是 | 角色：`STUDENT` / `TEACHER` / `ADMIN` |

**成功响应：**

```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIs...",
    "expireAt": 1718701200000,
    "user": {
      "userId": 1,
      "account": "2024001",
      "userName": "张三",
      "role": "STUDENT",
      "avatar": "/files/avatar/default.png",
      "department": "计算机科学学院",
      "className": "软件工程2024-1班"
    }
  }
}
```

**错误响应示例：**

```json
{ "code": 401, "message": "账号不存在", "data": null }
{ "code": 401, "message": "密码错误，剩余尝试次数：4", "data": null }
{ "code": 403, "message": "账号已被冻结，请联系管理员", "data": null }
{ "code": 423, "message": "密码连续错误超过5次，账号已锁定30分钟", "data": null }
```

---

### 2.2 忘记密码 — 验证身份

**POST** `/auth/forgot-password/verify`

**请求体：**

```json
{
  "account": "2024001",
  "role": "STUDENT",
  "reservedInfo": "张三"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| account | String | 是 | 账号 |
| role | String | 是 | 角色 |
| reservedInfo | String | 是 | 预留信息（姓名/手机号/邮箱） |

**成功响应：**

```json
{
  "code": 200,
  "message": "验证通过",
  "data": {
    "resetToken": "reset_abc123..."
  }
}
```

> `resetToken` 有效期 5 分钟，用于后续重置密码接口。

---

### 2.3 忘记密码 — 重置密码

**POST** `/auth/forgot-password/reset`

**请求体：**

```json
{
  "resetToken": "reset_abc123...",
  "newPassword": "newPass123"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| resetToken | String | 是 | 验证身份获取的重置令牌 |
| newPassword | String | 是 | 新密码（6-20位） |

**成功响应：**

```json
{ "code": 200, "message": "密码重置成功，请重新登录", "data": null }
```

---

### 2.4 退出登录

**POST** `/auth/logout`

**请求头：** `Authorization: Bearer {token}`

**成功响应：**

```json
{ "code": 200, "message": "已退出登录", "data": null }
```

> 服务端同时将 token 加入黑名单（Redis），闲置超时 30 分钟也会自动失效。

---

### 2.5 获取当前登录用户信息

**GET** `/auth/current-user`

**成功响应：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "userId": 1,
    "account": "2024001",
    "userName": "张三",
    "role": "STUDENT",
    "avatar": "/files/avatar/default.png",
    "email": "zhangsan@example.com",
    "phone": "13800138000",
    "department": "计算机科学学院",
    "className": "软件工程2024-1班",
    "status": "ACTIVE"
  }
}
```

---

### 2.6 修改密码（已登录状态）

**PUT** `/auth/password`

**请求体：**

```json
{
  "oldPassword": "123456",
  "newPassword": "newPass123"
}
```

**成功响应：**

```json
{ "code": 200, "message": "密码修改成功，请重新登录", "data": null }
```

---

## 3. 学生端接口

> **角色**: `STUDENT`  
> **权限**: 仅可操作本人数据

### 3.1 门户首页

#### 3.1.1 获取首页概览数据

**GET** `/student/dashboard`

**成功响应：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "todoList": [
      {
        "type": "COURSE",
        "title": "Java程序设计 第三章",
        "courseName": "Java程序设计",
        "deadline": "2026-06-25",
        "linkUrl": "/student/course/1"
      },
      {
        "type": "EXPERIMENT",
        "title": "实验二：排序算法实现",
        "courseName": "数据结构",
        "deadline": "2026-06-20",
        "linkUrl": "/student/experiment/5"
      },
      {
        "type": "TRAINING",
        "title": "Web项目实战",
        "courseName": "Web开发技术",
        "deadline": "2026-06-30",
        "linkUrl": "/student/training/3"
      }
    ],
    "notifications": [
      {
        "id": 1,
        "type": "ANNOUNCEMENT",
        "title": "关于期末考试安排的通知",
        "publishTime": "2026-06-18 10:00:00"
      },
      {
        "id": 2,
        "type": "CLASS_NOTICE",
        "title": "本周五实验课调至周六上午",
        "publisher": "李老师",
        "publishTime": "2026-06-17 14:30:00"
      }
    ],
    "todayStats": {
      "studyDuration": 125,
      "completedCourses": 2,
      "pendingTasks": 3
    }
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| todoList[].type | String | 类型：`COURSE` / `EXPERIMENT` / `TRAINING` |
| todayStats.studyDuration | Integer | 今日学习时长（分钟） |
| todayStats.completedCourses | Integer | 本周完成课程数 |
| todayStats.pendingTasks | Integer | 待完成任务数 |

---

### 3.2 课程学习

#### 3.2.1 获取我的课程列表

**GET** `/student/courses`

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| keyword | String | 否 | 课程名称搜索 |
| semester | String | 否 | 学期筛选，如 `2025-2026-2` |
| page | Integer | 否 | 页码 |
| pageSize | Integer | 否 | 每页条数 |

**成功响应：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "records": [
      {
        "courseId": 1,
        "courseName": "Java程序设计",
        "courseCode": "CS101",
        "teacherName": "王老师",
        "semester": "2025-2026-2",
        "progress": 65,
        "totalHours": 48,
        "completedHours": 31,
        "startDate": "2026-03-01",
        "coverImage": "/files/course/cs101.jpg"
      }
    ],
    "total": 8,
    "page": 1,
    "pageSize": 10,
    "totalPages": 1
  }
}
```

---

#### 3.2.2 获取课程详情

**GET** `/student/courses/{courseId}`

**成功响应：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "courseId": 1,
    "courseName": "Java程序设计",
    "courseCode": "CS101",
    "teacherName": "王老师",
    "teacherAvatar": "/files/avatar/t001.jpg",
    "semester": "2025-2026-2",
    "description": "本课程介绍Java编程语言的基础与进阶知识...",
    "progress": 65,
    "totalChapters": 12,
    "completedChapters": 8,
    "chapters": [
      {
        "chapterId": 1,
        "title": "第一章 Java入门",
        "sortOrder": 1,
        "duration": 120,
        "status": "COMPLETED",
        "videoUrl": "/files/video/java_ch1.mp4",
        "materials": [
          {
            "materialId": 1,
            "name": "第一章课件.pdf",
            "type": "PDF",
            "fileSize": 2048000,
            "downloadUrl": "/files/material/java_ch1.pdf"
          }
        ]
      },
      {
        "chapterId": 2,
        "title": "第二章 面向对象基础",
        "sortOrder": 2,
        "duration": 150,
        "status": "IN_PROGRESS",
        "lastPosition": 3600,
        "videoUrl": "/files/video/java_ch2.mp4",
        "materials": []
      }
    ]
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| chapters[].status | String | `NOT_STARTED` / `IN_PROGRESS` / `COMPLETED` |
| chapters[].lastPosition | Integer | 视频播放位置（秒），用于续播 |

---

#### 3.2.3 上报学习进度

**POST** `/student/courses/{courseId}/progress`

**请求体：**

```json
{
  "chapterId": 2,
  "position": 3720,
  "duration": 1800
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| chapterId | Long | 是 | 章节ID |
| position | Integer | 是 | 当前播放位置（秒） |
| duration | Integer | 是 | 本次观看时长（秒） |

> 前端每 30 秒自动调用一次，或暂停/关闭页面时调用。

**成功响应：**

```json
{
  "code": 200,
  "message": "进度已保存",
  "data": {
    "chapterProgress": 45,
    "courseProgress": 68
  }
}
```

---

#### 3.2.4 下载课件资料

**GET** `/student/courses/{courseId}/materials/{materialId}/download`

> 返回文件流，响应头 `Content-Disposition: attachment; filename="xxx.pdf"`

---

### 3.3 实验实训提交

#### 3.3.1 获取实验/实训任务列表

**GET** `/student/tasks`

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| type | String | 否 | `EXPERIMENT` / `TRAINING`，不传查全部 |
| status | String | 否 | `NOT_STARTED` / `IN_PROGRESS` / `SUBMITTED` / `GRADED` / `OVERDUE` / `RETURNED` |
| page | Integer | 否 | 页码 |
| pageSize | Integer | 否 | 每页条数 |

**成功响应：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "records": [
      {
        "taskId": 5,
        "taskType": "EXPERIMENT",
        "title": "实验二：排序算法实现",
        "courseName": "数据结构",
        "teacherName": "李老师",
        "deadline": "2026-06-20 23:59:59",
        "submitTime": null,
        "status": "IN_PROGRESS",
        "score": null,
        "comment": null,
        "retryCount": 0,
        "maxRetryCount": 2
      },
      {
        "taskId": 3,
        "taskType": "TRAINING",
        "title": "Web项目实战",
        "courseName": "Web开发技术",
        "teacherName": "赵老师",
        "deadline": "2026-06-15 23:59:59",
        "submitTime": "2026-06-14 20:30:00",
        "status": "GRADED",
        "score": 85,
        "comment": "整体完成度较高，注意代码注释规范",
        "retryCount": 0,
        "maxRetryCount": 2
      }
    ],
    "total": 5,
    "page": 1,
    "pageSize": 10,
    "totalPages": 1
  }
}
```

---

#### 3.3.2 获取任务详情

**GET** `/student/tasks/{taskId}`

**成功响应：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "taskId": 5,
    "taskType": "EXPERIMENT",
    "title": "实验二：排序算法实现",
    "courseName": "数据结构",
    "teacherName": "李老师",
    "deadline": "2026-06-20 23:59:59",
    "description": "## 实验要求\n1. 实现冒泡排序\n2. 实现快速排序\n3. 比较两种算法的性能",
    "guideFiles": [
      {
        "fileId": 1,
        "name": "实验指导书.pdf",
        "fileSize": 1024000,
        "downloadUrl": "/files/guide/exp2_guide.pdf"
      }
    ],
    "mySubmission": null,
    "status": "IN_PROGRESS"
  }
}
```

---

#### 3.3.3 提交实验/实训报告

**POST** `/student/tasks/{taskId}/submit`

**请求方式**: `multipart/form-data`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| content | String | 是 | 实训过程描述（文本） |
| reportFile | File | 否 | 报告文件（Word/PDF，最大 50MB） |
| attachmentFiles | File[] | 否 | 附件（代码、截图等） |

**成功响应：**

```json
{
  "code": 200,
  "message": "提交成功",
  "data": {
    "submissionId": 10,
    "submitTime": "2026-06-18 15:30:00",
    "status": "SUBMITTED"
  }
}
```

---

#### 3.3.4 重新提交（被退回后）

**POST** `/student/tasks/{taskId}/resubmit`

> 参数同 3.3.3，仅在 status 为 `RETURNED` 且 `retryCount < maxRetryCount` 时可调用。

---

#### 3.3.5 查看批改结果

**GET** `/student/tasks/{taskId}/result`

**成功响应：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "submissionId": 10,
    "score": 85,
    "comment": "排序实现正确，性能对比分析可以更深入",
    "gradedTime": "2026-06-19 10:00:00",
    "teacherName": "李老师",
    "canResubmit": false
  }
}
```

---

### 3.4 教学资源库

#### 3.4.1 资源列表/搜索

**GET** `/student/resources`

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| keyword | String | 否 | 关键词搜索资源名称 |
| category | String | 否 | 资源类型：`COURSEWARE` / `EXERCISE` / `VIDEO` / `DOCUMENT` / `OTHER` |
| courseId | Long | 否 | 按课程筛选 |
| scope | String | 否 | `ALL`（全部）/ `FAVORITE`（我的收藏） |
| page | Integer | 否 | 页码 |
| pageSize | Integer | 否 | 每页条数 |

**成功响应：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "records": [
      {
        "resourceId": 1,
        "name": "Java基础语法课件",
        "type": "COURSEWARE",
        "fileSize": 5120000,
        "uploadTime": "2026-05-10",
        "downloadCount": 328,
        "teacherName": "王老师",
        "courseName": "Java程序设计",
        "scope": "SCHOOL_WIDE",
        "isFavorited": true,
        "previewUrl": "/files/resource/java_basic_preview.pdf",
        "downloadUrl": "/files/resource/java_basic.pdf"
      }
    ],
    "total": 50,
    "page": 1,
    "pageSize": 10,
    "totalPages": 5
  }
}
```

---

#### 3.4.2 在线预览文件

**GET** `/student/resources/{resourceId}/preview`

> 返回文件流或预览地址，支持 PDF、图片、视频等格式的在线预览。

---

#### 3.4.3 下载资源

**GET** `/student/resources/{resourceId}/download`

> 返回文件流，下载计数 +1。

---

#### 3.4.4 添加/取消收藏

**POST** `/student/resources/{resourceId}/favorite`

**成功响应：**

```json
{ "code": 200, "message": "已收藏", "data": { "isFavorited": true } }
```

**DELETE** `/student/resources/{resourceId}/favorite`

```json
{ "code": 200, "message": "已取消收藏", "data": { "isFavorited": false } }
```

---

### 3.5 个人学情诊断

#### 3.5.1 获取学习数据统计

**GET** `/student/analytics/overview`

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| semester | String | 否 | 学期，默认当前学期 |

**成功响应：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "totalStudyDuration": 4800,
    "averageScore": 82.5,
    "taskCompletionRate": 88.0,
    "courseStats": [
      {
        "courseId": 1,
        "courseName": "Java程序设计",
        "studyDuration": 1800,
        "exerciseCorrectRate": 85.0,
        "taskCompletionRate": 90.0,
        "score": 88
      },
      {
        "courseId": 2,
        "courseName": "数据结构",
        "studyDuration": 1500,
        "exerciseCorrectRate": 78.0,
        "taskCompletionRate": 83.0,
        "score": 76
      }
    ],
    "weeklyTrend": [
      { "week": "第14周", "duration": 320, "taskCount": 4 },
      { "week": "第15周", "duration": 280, "taskCount": 3 },
      { "week": "第16周", "duration": 400, "taskCount": 5 }
    ]
  }
}
```

---

#### 3.5.2 获取学业诊断报告

**GET** `/student/analytics/diagnosis`

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| semester | String | 否 | 学期，默认当前学期 |

**成功响应：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "academicLevel": "GOOD",
    "levelLabel": "良好",
    "overallScore": 82.5,
    "summary": "你在本学期各课程中表现良好，Java程序设计掌握扎实，数据结构的算法部分需要加强练习。",
    "weakPoints": [
      { "knowledge": "排序算法", "courseName": "数据结构", "suggestResourceId": 15 },
      { "knowledge": "树与图", "courseName": "数据结构", "suggestResourceId": 18 }
    ],
    "strengths": [
      { "knowledge": "面向对象编程", "courseName": "Java程序设计" }
    ],
    "suggestResources": [
      {
        "resourceId": 15,
        "name": "排序算法详解视频",
        "type": "VIDEO",
        "courseName": "数据结构"
      },
      {
        "resourceId": 18,
        "name": "树与图专题习题集",
        "type": "EXERCISE",
        "courseName": "数据结构"
      }
    ],
    "generatedTime": "2026-06-18 08:00:00"
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| academicLevel | String | `EXCELLENT`（优秀）/ `GOOD`（良好）/ `NEED_IMPROVE`（待提升） |

---

### 3.6 个人中心

#### 3.6.1 修改个人资料

**PUT** `/student/profile`

**请求体：**

```json
{
  "avatar": "/files/avatar/user1_new.jpg",
  "nickname": "小张",
  "phone": "13900139000",
  "email": "zhang3@example.com"
}
```

> 所有字段可选，只传需要修改的字段。

**成功响应：**

```json
{ "code": 200, "message": "信息修改成功", "data": null }
```

---

#### 3.6.2 上传头像

**POST** `/student/profile/avatar`

**请求方式**: `multipart/form-data`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| avatar | File | 是 | 图片文件（jpg/png，最大 2MB） |

**成功响应：**

```json
{
  "code": 200,
  "message": "头像上传成功",
  "data": { "avatarUrl": "/files/avatar/user1_20260618.jpg" }
}
```

---

#### 3.6.3 成绩查询

**GET** `/student/grades`

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| semester | String | 否 | 学期筛选 |
| page | Integer | 否 | 页码 |
| pageSize | Integer | 否 | 每页条数 |

**成功响应：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "records": [
      {
        "courseId": 1,
        "courseName": "Java程序设计",
        "semester": "2025-2026-2",
        "teacherName": "王老师",
        "dailyScore": 90,
        "examScore": 86,
        "experimentScore": 88,
        "trainingScore": null,
        "finalScore": 88,
        "credit": 3.0,
        "gpa": 3.7
      }
    ],
    "total": 8,
    "page": 1,
    "pageSize": 10,
    "totalPages": 1
  }
}
```

---

#### 3.6.4 消息中心

**GET** `/student/messages`

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| type | String | 否 | `NOTICE` / `GRADE` / `ANNOUNCEMENT` / `WARNING` / `SYSTEM` |
| isRead | Boolean | 否 | 是否已读 |
| page | Integer | 否 | 页码 |
| pageSize | Integer | 否 | 每页条数 |

**成功响应：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "records": [
      {
        "messageId": 1,
        "type": "GRADE",
        "title": "实验报告已批改",
        "content": "您的《数据结构》实验二报告已批改，得分85分，请查看详情。",
        "isRead": false,
        "relatedId": 5,
        "relatedType": "TASK",
        "sendTime": "2026-06-19 10:05:00"
      }
    ],
    "total": 20,
    "unreadCount": 5,
    "page": 1,
    "pageSize": 10,
    "totalPages": 2
  }
}
```

---

#### 3.6.5 标记消息已读

**PUT** `/student/messages/{messageId}/read`

```json
{ "code": 200, "message": "已标记已读", "data": null }
```

#### 3.6.6 全部标记已读

**PUT** `/student/messages/read-all`

```json
{ "code": 200, "message": "全部已读", "data": null }
```

---

## 4. 教师端接口

> **角色**: `TEACHER`  
> **权限**: 仅可操作本人授课班级及资源数据

### 4.1 工作台首页

#### 4.1.1 获取工作台概览数据

**GET** `/teacher/dashboard`

**成功响应：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "pendingReviews": {
      "experiments": 12,
      "trainings": 5,
      "total": 17
    },
    "pendingAudits": {
      "courses": 1,
      "tasks": 2,
      "resources": 3,
      "total": 6
    },
    "atRiskStudents": [
      {
        "userId": 10,
        "userName": "李四",
        "className": "软件工程2024-1班",
        "reason": "连续2周未提交作业",
        "missedTasks": 3
      }
    ],
    "notifications": [
      {
        "id": 1,
        "type": "AUDIT_RESULT",
        "title": "课程《Java程序设计》开课申请已通过",
        "publishTime": "2026-06-18 09:00:00"
      }
    ],
    "classSummary": {
      "totalStudents": 45,
      "avgCompletionRate": 78.5,
      "avgScore": 76.3
    }
  }
}
```

---

### 4.2 课程计划管理

#### 4.2.1 获取我的课程列表

**GET** `/teacher/courses`

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| semester | String | 否 | 学期 |
| auditStatus | String | 否 | `PENDING` / `APPROVED` / `REJECTED` |
| keyword | String | 否 | 课程名称搜索 |
| page | Integer | 否 | 页码 |
| pageSize | Integer | 否 | 每页条数 |

**成功响应：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "records": [
      {
        "courseId": 1,
        "courseName": "Java程序设计",
        "courseCode": "CS101",
        "semester": "2025-2026-2",
        "className": "软件工程2024-1班",
        "studentCount": 45,
        "totalHours": 48,
        "weeklyHours": 4,
        "startDate": "2026-03-01",
        "endDate": "2026-07-01",
        "auditStatus": "APPROVED",
        "auditComment": null,
        "progress": 65
      }
    ],
    "total": 5,
    "page": 1,
    "pageSize": 10,
    "totalPages": 1
  }
}
```

---

#### 4.2.2 创建开课计划

**POST** `/teacher/courses`

**请求体：**

```json
{
  "courseName": "Java程序设计",
  "courseCode": "CS101",
  "semester": "2025-2026-2",
  "classId": 1,
  "totalHours": 48,
  "weeklyHours": 4,
  "startDate": "2026-03-01",
  "endDate": "2026-07-01",
  "description": "本课程介绍Java编程语言...",
  "chapters": [
    { "title": "第一章 Java入门", "sortOrder": 1, "duration": 120 },
    { "title": "第二章 面向对象基础", "sortOrder": 2, "duration": 150 }
  ]
}
```

**成功响应：**

```json
{
  "code": 200,
  "message": "开课计划已创建，请等待管理员审核",
  "data": {
    "courseId": 1,
    "auditStatus": "PENDING"
  }
}
```

---

#### 4.2.3 编辑课程信息

**PUT** `/teacher/courses/{courseId}`

> 仅 `PENDING` 或 `REJECTED` 状态可编辑。参数同 4.2.2。

---

#### 4.2.4 删除课程（未审核通过状态）

**DELETE** `/teacher/courses/{courseId}`

> 仅 `PENDING` 或 `REJECTED` 状态可删除。

---

#### 4.2.5 获取课程学习进度（全班）

**GET** `/teacher/courses/{courseId}/progress`

**成功响应：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "courseName": "Java程序设计",
    "overallProgress": 65,
    "chapterProgress": [
      { "chapterId": 1, "title": "第一章", "avgProgress": 95 },
      { "chapterId": 2, "title": "第二章", "avgProgress": 72 }
    ],
    "studentProgress": [
      { "userId": 1, "userName": "张三", "progress": 88, "lastStudyTime": "2026-06-18 10:00:00" },
      { "userId": 2, "userName": "李四", "progress": 45, "lastStudyTime": "2026-06-15 16:00:00" }
    ]
  }
}
```

---

#### 4.2.6 录入/编辑学生成绩

**POST** `/teacher/courses/{courseId}/grades`

**请求体：**

```json
{
  "grades": [
    { "userId": 1, "dailyScore": 90, "examScore": 86, "experimentScore": 88 },
    { "userId": 2, "dailyScore": 75, "examScore": 70, "experimentScore": 72 }
  ]
}
```

**成功响应：**

```json
{ "code": 200, "message": "成绩录入成功", "data": { "updatedCount": 2 } }
```

---

#### 4.2.7 导出全班成绩

**GET** `/teacher/courses/{courseId}/grades/export`

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| format | String | 否 | 导出格式：`EXCEL` / `CSV`，默认 EXCEL |

> 返回文件流。

---

### 4.3 实训实验计划管理

#### 4.3.1 获取我的实验/实训项目列表

**GET** `/teacher/tasks`

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| taskType | String | 否 | `EXPERIMENT` / `TRAINING` |
| courseId | Long | 否 | 按课程筛选 |
| auditStatus | String | 否 | `PENDING` / `APPROVED` / `REJECTED` |
| page | Integer | 否 | 页码 |
| pageSize | Integer | 否 | 每页条数 |

---

#### 4.3.2 创建实验/实训项目

**POST** `/teacher/tasks`

**请求体：**

```json
{
  "taskType": "EXPERIMENT",
  "title": "实验二：排序算法实现",
  "courseId": 2,
  "classId": 1,
  "description": "## 实验要求\n1. 实现冒泡排序\n2. 实现快速排序",
  "startTime": "2026-06-10 08:00:00",
  "endTime": "2026-06-20 23:59:59",
  "maxRetryCount": 2,
  "guideFiles": []
}
```

**成功响应：**

```json
{
  "code": 200,
  "message": "实验项目已创建，请等待管理员审核",
  "data": { "taskId": 5, "auditStatus": "PENDING" }
}
```

---

#### 4.3.3 上传指导文档

**POST** `/teacher/tasks/{taskId}/guide-files`

**请求方式**: `multipart/form-data`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| files | File[] | 是 | 指导文档（PDF/Word，单文件最大 50MB） |

---

#### 4.3.4 编辑实验/实训项目

**PUT** `/teacher/tasks/{taskId}`

> 仅 `PENDING` 或 `REJECTED` 状态可编辑。

---

#### 4.3.5 删除实验/实训项目

**DELETE** `/teacher/tasks/{taskId}`

---

#### 4.3.6 获取学生提交列表

**GET** `/teacher/tasks/{taskId}/submissions`

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| status | String | 否 | `SUBMITTED` / `GRADED` / `RETURNED` |
| page | Integer | 否 | 页码 |
| pageSize | Integer | 否 | 每页条数 |

**成功响应：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "taskTitle": "实验二：排序算法实现",
    "totalStudents": 45,
    "submittedCount": 38,
    "gradedCount": 25,
    "records": [
      {
        "submissionId": 10,
        "userId": 1,
        "userName": "张三",
        "submitTime": "2026-06-15 20:30:00",
        "status": "SUBMITTED",
        "score": null,
        "isLate": false,
        "fileUrl": "/files/submission/exp2_zhangsan.pdf"
      }
    ],
    "total": 38,
    "page": 1,
    "pageSize": 10,
    "totalPages": 4
  }
}
```

---

#### 4.3.7 批阅学生提交

**POST** `/teacher/submissions/{submissionId}/grade`

**请求体：**

```json
{
  "score": 85,
  "comment": "排序实现正确，但性能对比分析可以更深入",
  "action": "PASS"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| score | Integer | 是 | 分数（0-100） |
| comment | String | 否 | 批改评语 |
| action | String | 是 | `PASS`（通过）/ `RETURN`（退回重做） |

---

#### 4.3.8 退回学生提交

**POST** `/teacher/submissions/{submissionId}/return`

**请求体：**

```json
{
  "returnReason": "排序算法的性能对比分析缺失，请补充后重新提交"
}
```

**成功响应：**

```json
{
  "code": 200,
  "message": "已退回，学生可重新提交",
  "data": { "retryCount": 1, "maxRetryCount": 2 }
}
```

---

### 4.4 教学资源上传

#### 4.4.1 获取我的资源列表

**GET** `/teacher/resources`

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| auditStatus | String | 否 | `PENDING` / `APPROVED` / `REJECTED` |
| type | String | 否 | `COURSEWARE` / `EXERCISE` / `VIDEO` / `DOCUMENT` / `OTHER` |
| keyword | String | 否 | 关键词搜索 |
| page | Integer | 否 | 页码 |
| pageSize | Integer | 否 | 每页条数 |

---

#### 4.4.2 上传资源

**POST** `/teacher/resources`

**请求方式**: `multipart/form-data`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| files | File[] | 是 | 资源文件（单文件最大 100MB） |
| name | String | 是 | 资源名称 |
| type | String | 是 | `COURSEWARE` / `EXERCISE` / `VIDEO` / `DOCUMENT` / `OTHER` |
| courseId | Long | 否 | 关联课程 |
| scope | String | 是 | `CLASS_ONLY` / `DEPARTMENT_WIDE` / `SCHOOL_WIDE` |
| description | String | 否 | 资源描述 |

**成功响应：**

```json
{
  "code": 200,
  "message": "上传成功，请等待管理员审核",
  "data": {
    "resourceId": 10,
    "auditStatus": "PENDING"
  }
}
```

---

#### 4.4.3 编辑资源信息

**PUT** `/teacher/resources/{resourceId}`

> 仅 `PENDING` 或 `REJECTED` 状态可编辑。

---

#### 4.4.4 删除资源

**DELETE** `/teacher/resources/{resourceId}`

> 仅 `PENDING` 或 `REJECTED` 状态可删除。

---

#### 4.4.5 查看驳回意见并重新提交

**GET** `/teacher/resources/{resourceId}/audit-feedback`

**成功响应：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "auditStatus": "REJECTED",
    "auditComment": "课件内容含有错误信息，请修正后重新提交",
    "auditTime": "2026-06-18 14:00:00",
    "auditorName": "管理员张"
  }
}
```

**POST** `/teacher/resources/{resourceId}/resubmit`

> 修改后重新提交审核，参数同 4.4.2。

---

### 4.5 班级学情查看

#### 4.5.1 班级学情总览

**GET** `/teacher/analytics/class-overview`

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| classId | Long | 否 | 班级ID，默认所有授课班级 |
| semester | String | 否 | 学期 |

**成功响应：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "className": "软件工程2024-1班",
    "studentCount": 45,
    "avgCourseCompletionRate": 78.5,
    "avgExerciseCorrectRate": 72.3,
    "avgExperimentCompletionRate": 85.0,
    "avgTrainingCompletionRate": 80.0,
    "levelDistribution": {
      "excellent": 8,
      "good": 20,
      "needImprove": 17
    },
    "courseRanking": [
      { "courseName": "Java程序设计", "avgScore": 82, "completionRate": 90 },
      { "courseName": "数据结构", "avgScore": 72, "completionRate": 78 }
    ]
  }
}
```

---

#### 4.5.2 查看单个学生学情

**GET** `/teacher/analytics/student/{userId}`

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| semester | String | 否 | 学期 |

> 响应格式与学生端 3.5 个人学情诊断类似。

---

#### 4.5.3 筛选异常学生

**GET** `/teacher/analytics/at-risk-students`

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| classId | Long | 否 | 班级筛选 |
| filterType | String | 是 | `MISSING_HOMEWORK` / `LOW_SCORE` / `SLOW_PROGRESS` / `ALL` |

**成功响应：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "userId": 10,
      "userName": "李四",
      "className": "软件工程2024-1班",
      "missedTasks": 3,
      "avgScore": 55,
      "progress": 35,
      "lastLoginTime": "2026-06-10",
      "riskLevel": "HIGH"
    }
  ]
}
```

---

#### 4.5.4 导出班级学情报表

**GET** `/teacher/analytics/export`

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| classId | Long | 是 | 班级ID |
| semester | String | 否 | 学期 |
| format | String | 否 | `EXCEL` / `CSV`，默认 EXCEL |

> 返回文件流。

---

### 4.6 个人消息公告

#### 4.6.1 发布班级公告

**POST** `/teacher/notices`

**请求体：**

```json
{
  "classId": 1,
  "title": "本周五实验课调至周六上午",
  "content": "因教室临时占用，本周五的实验课调整至周六上午9:00，地点不变。",
  "importance": "NORMAL"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| classId | Long | 是 | 目标班级ID |
| title | String | 是 | 公告标题 |
| content | String | 是 | 公告内容 |
| importance | String | 否 | `NORMAL` / `IMPORTANT`，默认 NORMAL |

---

#### 4.6.2 获取我发布的公告列表

**GET** `/teacher/notices`

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| classId | Long | 否 | 班级筛选 |
| page | Integer | 否 | 页码 |
| pageSize | Integer | 否 | 每页条数 |

---

#### 4.6.3 删除公告

**DELETE** `/teacher/notices/{noticeId}`

---

#### 4.6.4 获取我的消息

**GET** `/teacher/messages`

> 参考学生端 3.6.4，消息类型包括：`AUDIT_RESULT` / `WARNING` / `SYSTEM` / `ANNOUNCEMENT`

---

## 5. 管理员端接口

> **角色**: `ADMIN`  
> **权限**: 全平台管理权限

### 5.1 用户管理

#### 5.1.1 获取用户列表

**GET** `/admin/users`

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| role | String | 否 | `STUDENT` / `TEACHER` / `ADMIN` |
| keyword | String | 否 | 姓名/学号/工号搜索 |
| department | String | 否 | 院系筛选 |
| className | String | 否 | 班级筛选 |
| status | String | 否 | `ACTIVE` / `FROZEN` / `LOCKED` |
| page | Integer | 否 | 页码 |
| pageSize | Integer | 否 | 每页条数 |

**成功响应：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "records": [
      {
        "userId": 1,
        "account": "2024001",
        "userName": "张三",
        "role": "STUDENT",
        "department": "计算机科学学院",
        "className": "软件工程2024-1班",
        "email": "zhangsan@example.com",
        "phone": "13800138000",
        "status": "ACTIVE",
        "createTime": "2026-03-01",
        "lastLoginTime": "2026-06-18 10:00:00"
      }
    ],
    "total": 500,
    "page": 1,
    "pageSize": 10,
    "totalPages": 50
  }
}
```

---

#### 5.1.2 批量导入学生账号

**POST** `/admin/users/import`

**请求方式**: `multipart/form-data`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | File | 是 | Excel 文件（.xlsx），按模板格式 |

**Excel 模板格式：**

| 学号 | 姓名 | 院系 | 班级 | 初始密码 | 邮箱 | 手机号 |
|------|------|------|------|----------|------|--------|
| 2024001 | 张三 | 计算机科学学院 | 软件工程2024-1班 | 123456 | zs@xx.com | 138xxx |

**成功响应：**

```json
{
  "code": 200,
  "message": "导入完成",
  "data": {
    "successCount": 45,
    "failCount": 2,
    "failDetails": [
      { "row": 3, "account": "2024003", "reason": "学号已存在" },
      { "row": 15, "account": "", "reason": "学号为空" }
    ]
  }
}
```

---

#### 5.1.3 单独新增用户

**POST** `/admin/users`

**请求体：**

```json
{
  "account": "t0010",
  "userName": "陈老师",
  "role": "TEACHER",
  "password": "123456",
  "department": "计算机科学学院",
  "email": "chen@example.com",
  "phone": "13900139000"
}
```

---

#### 5.1.4 编辑用户信息

**PUT** `/admin/users/{userId}`

```json
{
  "userName": "张三丰",
  "department": "计算机科学学院",
  "className": "软件工程2024-2班",
  "email": "newemail@xx.com"
}
```

---

#### 5.1.5 启用/冻结账号

**PUT** `/admin/users/{userId}/status`

**请求体：**

```json
{
  "status": "FROZEN",
  "reason": "违规操作，冻结账号7天"
}
```

| status | 说明 |
|--------|------|
| `ACTIVE` | 正常 |
| `FROZEN` | 冻结 |
| `LOCKED` | 自动锁定（密码错误超限），管理员可解锁 |

---

#### 5.1.6 重置用户密码

**PUT** `/admin/users/{userId}/reset-password`

**请求体：**

```json
{
  "newPassword": "reset123456"
}
```

> 不传 `newPassword` 则重置为系统默认密码。

---

#### 5.1.7 获取用户详情

**GET** `/admin/users/{userId}`

---

### 5.2 教学内容统一审核模块

#### 5.2.1 课程开课计划审核

##### 获取待审核课程列表

**GET** `/admin/audit/courses`

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| status | String | 否 | `PENDING` / `APPROVED` / `REJECTED`，默认 PENDING |
| semester | String | 否 | 学期 |
| department | String | 否 | 院系 |
| page | Integer | 否 | 页码 |
| pageSize | Integer | 否 | 每页条数 |

---

##### 审核课程

**POST** `/admin/audit/courses/{courseId}`

**请求体：**

```json
{
  "action": "APPROVE",
  "comment": "开课计划内容完整，予以通过"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| action | String | 是 | `APPROVE` / `REJECT` |
| comment | String | 是 | 审核意见（通过/驳回原因） |

---

##### 获取审核日志

**GET** `/admin/audit/courses/logs`

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| courseId | Long | 否 | 课程ID |
| auditorId | Long | 否 | 审核人ID |
| startDate | String | 否 | 开始日期 |
| endDate | String | 否 | 结束日期 |
| page | Integer | 否 | 页码 |
| pageSize | Integer | 否 | 每页条数 |

---

##### 全校开课数据统计

**GET** `/admin/audit/courses/statistics`

**成功响应：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "totalCourses": 120,
    "approvedCourses": 110,
    "pendingCourses": 8,
    "rejectedCourses": 2,
    "byDepartment": [
      { "department": "计算机科学学院", "total": 35, "approved": 33, "pending": 2 },
      { "department": "数学学院", "total": 20, "approved": 19, "pending": 1 }
    ],
    "bySemester": [
      { "semester": "2025-2026-2", "total": 60, "approved": 55, "pending": 5 }
    ]
  }
}
```

---

#### 5.2.2 实训实验计划审核

##### 获取待审核实训/实验列表

**GET** `/admin/audit/tasks`

> 参数结构与课程审核类似，增加 `taskType` 参数（`EXPERIMENT` / `TRAINING`）。

---

##### 审核实训/实验

**POST** `/admin/audit/tasks/{taskId}`

```json
{
  "action": "APPROVE",
  "comment": "实验内容符合教学大纲要求"
}
```

---

##### 实训完成率统计

**GET** `/admin/audit/tasks/statistics`

**成功响应：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "byDepartment": [
      {
        "department": "计算机科学学院",
        "totalTasks": 50,
        "avgCompletionRate": 85.0,
        "avgPassRate": 92.0
      }
    ]
  }
}
```

---

#### 5.2.3 线上教学资源审核

##### 获取待审核资源列表

**GET** `/admin/audit/resources`

> 参数结构类似，增加 `type` 参数（`COURSEWARE` / `EXERCISE` / `VIDEO` / `DOCUMENT` / `OTHER`）。

---

##### 审核资源

**POST** `/admin/audit/resources/{resourceId}`

```json
{
  "action": "REJECT",
  "comment": "课件内容存在多处错误，请修改后重新上传"
}
```

---

##### 资源使用统计

**GET** `/admin/audit/resources/statistics`

**成功响应：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "totalResources": 500,
    "totalDownloads": 15000,
    "totalViews": 50000,
    "byType": [
      { "type": "COURSEWARE", "count": 200, "downloads": 8000 },
      { "type": "VIDEO", "count": 150, "downloads": 4000 }
    ],
    "topResources": [
      { "resourceId": 10, "name": "Java基础语法课件", "downloads": 1200 }
    ]
  }
}
```

---

### 5.3 系统设置

#### 5.3.1 获取系统参数配置

**GET** `/admin/settings`

**成功响应：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "currentSemester": "2025-2026-2",
    "currentSchoolYear": "2025-2026",
    "maxFileUploadSize": 104857600,
    "allowedFileTypes": ["pdf", "doc", "docx", "ppt", "pptx", "mp4", "jpg", "png", "zip"],
    "academicThresholds": {
      "excellentMinScore": 85,
      "goodMinScore": 70,
      "needImproveMaxScore": 69
    },
    "passwordRule": {
      "defaultPassword": "123456",
      "minLength": 6,
      "maxLength": 20,
      "requireComplexity": false
    }
  }
}
```

---

#### 5.3.2 更新系统参数

**PUT** `/admin/settings`

**请求体：**

```json
{
  "currentSemester": "2025-2026-2",
  "maxFileUploadSize": 157286400,
  "allowedFileTypes": ["pdf", "doc", "docx", "mp4"],
  "academicThresholds": {
    "excellentMinScore": 90,
    "goodMinScore": 75,
    "needImproveMaxScore": 74
  },
  "passwordRule": {
    "defaultPassword": "abcd1234",
    "minLength": 8,
    "maxLength": 20,
    "requireComplexity": true
  }
}
```

> 所有字段可选，只传需要修改的配置项。

---

#### 5.3.3 发布全校/院系公告

**POST** `/admin/announcements`

**请求体：**

```json
{
  "title": "关于期末考试安排的通知",
  "content": "本学期期末考试将于2026年7月5日开始...",
  "scope": "SCHOOL_WIDE",
  "department": null,
  "importance": "IMPORTANT",
  "attachmentFiles": []
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| scope | String | 是 | `SCHOOL_WIDE` / `DEPARTMENT_WIDE` |
| department | String | 否 | scope 为 `DEPARTMENT_WIDE` 时必填 |
| importance | String | 否 | `NORMAL` / `IMPORTANT` |

---

#### 5.3.4 获取公告列表

**GET** `/admin/announcements`

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| scope | String | 否 | `SCHOOL_WIDE` / `DEPARTMENT_WIDE` |
| keyword | String | 否 | 标题搜索 |
| startDate | String | 否 | 发布日期起 |
| endDate | String | 否 | 发布日期止 |
| page | Integer | 否 | 页码 |
| pageSize | Integer | 否 | 每页条数 |

---

#### 5.3.5 编辑/删除公告

**PUT** `/admin/announcements/{announcementId}`

**DELETE** `/admin/announcements/{announcementId}`

---

### 5.4 全局学情监控

#### 5.4.1 全校学情总览

**GET** `/admin/analytics/overview`

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| semester | String | 否 | 学期 |

**成功响应：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "totalStudents": 2000,
    "totalCourses": 120,
    "overallCompletionRate": 78.5,
    "overallPassRate": 85.0,
    "levelDistribution": {
      "excellent": 350,
      "good": 800,
      "needImprove": 850
    },
    "byDepartment": [
      {
        "department": "计算机科学学院",
        "studentCount": 500,
        "completionRate": 82.0,
        "passRate": 88.0,
        "levelDistribution": { "excellent": 100, "good": 220, "needImprove": 180 }
      }
    ],
    "trendData": [
      { "month": "2026-03", "avgScore": 76, "completionRate": 75 },
      { "month": "2026-04", "avgScore": 78, "completionRate": 77 },
      { "month": "2026-05", "avgScore": 80, "completionRate": 80 }
    ]
  }
}
```

---

#### 5.4.2 预警名单

**GET** `/admin/analytics/warnings`

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| department | String | 否 | 院系筛选 |
| riskLevel | String | 否 | `HIGH` / `MEDIUM` / `LOW` |
| warningType | String | 否 | `LONG_ABSENCE` / `MISSING_HOMEWORK` / `LOW_SCORE` / `ALL` |
| page | Integer | 否 | 页码 |
| pageSize | Integer | 否 | 每页条数 |

**成功响应：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "records": [
      {
        "userId": 10,
        "userName": "李四",
        "account": "2024100",
        "department": "计算机科学学院",
        "className": "软件工程2024-1班",
        "warningType": "LONG_ABSENCE",
        "lastLoginTime": "2026-06-01",
        "missedTasks": 5,
        "avgScore": 45,
        "riskLevel": "HIGH",
        "warningTime": "2026-06-18 08:00:00"
      }
    ],
    "total": 50,
    "summary": {
      "highRisk": 15,
      "mediumRisk": 20,
      "lowRisk": 15
    },
    "page": 1,
    "pageSize": 10,
    "totalPages": 5
  }
}
```

---

#### 5.4.3 导出学期学情汇总报表

**GET** `/admin/analytics/export`

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| semester | String | 是 | 学期 |
| department | String | 否 | 院系（不传则导出全校） |
| format | String | 否 | `EXCEL` / `CSV`，默认 EXCEL |

> 返回文件流。

---

## 6. 公共接口

### 6.1 文件上传（通用）

**POST** `/common/upload`

**请求方式**: `multipart/form-data`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | File | 是 | 文件（受限于系统设置的文件大小和类型） |
| module | String | 是 | 业务模块标识：`avatar` / `course_material` / `resource` / `submission` / `guide` |

**成功响应：**

```json
{
  "code": 200,
  "message": "上传成功",
  "data": {
    "fileId": 123,
    "fileName": "课件.pdf",
    "fileSize": 5120000,
    "fileUrl": "/files/resource/2026/06/abc123.pdf",
    "uploadTime": "2026-06-18 15:30:00"
  }
}
```

> 支持断点续传（通过 `Content-Range` 请求头）。

---

### 6.2 获取院系列表

**GET** `/common/departments`

```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    { "departmentId": 1, "departmentName": "计算机科学学院" },
    { "departmentId": 2, "departmentName": "数学学院" }
  ]
}
```

---

### 6.3 获取班级列表

**GET** `/common/classes`

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| departmentId | Long | 否 | 院系筛选 |
| keyword | String | 否 | 班级名称搜索 |

---

### 6.4 获取学期列表

**GET** `/common/semesters`

```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    "2024-2025-1",
    "2024-2025-2",
    "2025-2026-1",
    "2025-2026-2"
  ]
}
```

---

### 6.5 获取课程基础信息列表（下拉选择用）

**GET** `/common/courses`

> 返回课程 ID 和名称的简要列表，供下拉框选择使用。

---

### 6.6 获取当前系统学期配置

**GET** `/common/current-semester`

```json
{ "code": 200, "message": "操作成功", "data": { "semester": "2025-2026-2" } }
```

---

## 7. 错误码对照表

| 错误码 | 说明 |
|--------|------|
| 200 | 操作成功 |
| 400 | 请求参数错误 |
| 401 | 未登录 / 登录已过期 |
| 403 | 无权限 / 角色越权 |
| 404 | 资源不存在 |
| 409 | 数据冲突（如重复提交） |
| 413 | 上传文件过大 |
| 415 | 不支持的附件格式 |
| 422 | 业务逻辑校验失败 |
| 423 | 账号已锁定 |
| 429 | 请求过于频繁 |
| 500 | 服务器内部错误 |

**校验失败响应示例（422）：**

```json
{
  "code": 422,
  "message": "参数校验失败",
  "data": {
    "errors": [
      { "field": "deadline", "message": "截止时间不能早于当前时间" },
      { "field": "title", "message": "标题不能为空" }
    ]
  }
}
```

---

## 附录 A：通用枚举值

### A.1 角色（Role）

| 值 | 说明 |
|----|------|
| `STUDENT` | 学生 |
| `TEACHER` | 教师 |
| `ADMIN` | 管理员 |

### A.2 账号状态（UserStatus）

| 值 | 说明 |
|----|------|
| `ACTIVE` | 正常 |
| `FROZEN` | 已冻结（管理员操作） |
| `LOCKED` | 已锁定（密码错误超限自动锁定） |

### A.3 审核状态（AuditStatus）

| 值 | 说明 |
|----|------|
| `PENDING` | 待审核 |
| `APPROVED` | 审核通过 |
| `REJECTED` | 审核驳回 |

### A.4 任务类型（TaskType）

| 值 | 说明 |
|----|------|
| `EXPERIMENT` | 实验 |
| `TRAINING` | 实训 |

### A.5 资源类型（ResourceType）

| 值 | 说明 |
|----|------|
| `COURSEWARE` | 课件 |
| `EXERCISE` | 习题 |
| `VIDEO` | 视频 |
| `DOCUMENT` | 文档 |
| `OTHER` | 其他 |

### A.6 资源可见范围（ResourceScope）

| 值 | 说明 |
|----|------|
| `CLASS_ONLY` | 仅本班可见 |
| `DEPARTMENT_WIDE` | 本院系可见 |
| `SCHOOL_WIDE` | 全校可见 |

### A.7 学业等级（AcademicLevel）

| 值 | 说明 |
|----|------|
| `EXCELLENT` | 优秀 |
| `GOOD` | 良好 |
| `NEED_IMPROVE` | 待提升 |

### A.8 消息类型（MessageType）

| 值 | 说明 |
|----|------|
| `NOTICE` | 班级通知 |
| `GRADE` | 批改提醒 |
| `ANNOUNCEMENT` | 系统公告 |
| `WARNING` | 学情预警 |
| `AUDIT_RESULT` | 审批结果 |
| `SYSTEM` | 系统消息 |

---

## 附录 B：接口性能要求

| 接口类型 | 响应时间要求 |
|----------|-------------|
| 普通列表/详情查询 | ≤ 500ms |
| 学情统计图表/复杂查询 | ≤ 2s |
| 文件上传 | 单文件最大 100MB，支持断点续传 |
| 文件下载 | 流式传输，无超时限制 |
| 并发支持 | 多人同时在线，无数据丢失 |

---

> **文档维护说明**: 开发过程中接口如有变更，请及时更新本文档，确保前后端接口约定一致。
