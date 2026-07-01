package com.iotp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.iotp.common.BusinessException;
import com.iotp.entity.*;
import com.iotp.mapper.*;
import com.iotp.security.UserContext;
import com.iotp.service.AdminService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理员端业务服务实现
 */
@Service
public class AdminServiceImpl implements AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminServiceImpl.class);

    /** 默认每页大小 */
    private static final int DEFAULT_PAGE_SIZE = 10;

    /** 审核状态常量 */
    private static final String AUDIT_PENDING = "PENDING";
    private static final String AUDIT_APPROVED = "APPROVED";
    private static final String AUDIT_REJECTED = "REJECTED";

    /** 用户状态常量 */
    private static final String USER_STATUS_ACTIVE = "ACTIVE";
    private static final String USER_STATUS_FROZEN = "FROZEN";
    private static final String USER_STATUS_LOCKED = "LOCKED";

    /** 公告范围常量 */
    private static final String SCOPE_ALL = "ALL";
    private static final String SCOPE_DEPARTMENT = "DEPARTMENT";
    private static final String SCOPE_CLASS = "CLASS";

    /** 默认密码配置键 */
    private static final String CONFIG_DEFAULT_PASSWORD = "default_password";

    // ==================== 注入 Mapper ====================

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysRoleMapper sysRoleMapper;

    @Autowired
    private SysDepartmentMapper sysDepartmentMapper;

    @Autowired
    private SysClassMapper sysClassMapper;

    @Autowired
    private SysSemesterMapper sysSemesterMapper;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private CoursePlanMapper coursePlanMapper;

    @Autowired
    private ExperimentProjectMapper experimentProjectMapper;

    @Autowired
    private ExperimentTaskMapper experimentTaskMapper;

    @Autowired
    private TeachingResourceMapper teachingResourceMapper;

    @Autowired
    private ResourceCategoryMapper resourceCategoryMapper;

    @Autowired
    private StudentGradeMapper studentGradeMapper;

    @Autowired
    private StudentCourseEnrollmentMapper enrollmentMapper;

    @Autowired
    private AcademicDiagnosisMapper academicDiagnosisMapper;

    @Autowired
    private AcademicDiagnosisRuleMapper academicDiagnosisRuleMapper;

    @Autowired
    private SysAnnouncementMapper sysAnnouncementMapper;

    @Autowired
    private SysMessageMapper sysMessageMapper;

    @Autowired
    private SysConfigMapper sysConfigMapper;

    @Autowired
    private SysAuditLogMapper sysAuditLogMapper;

    @Autowired
    private com.iotp.mapper.SysOperationLogMapper sysOperationLogMapper;

    @Autowired
    private LearningAnalyticsDailyMapper analyticsDailyMapper;

    @Autowired
    private StudentExperimentSubmissionMapper submissionMapper;

    // ==================== 用户管理 ====================

    @Override
    public IPage<Map<String, Object>> getUsers(String role, String keyword, Long department,
                                                String className, Integer status,
                                                Integer page, Integer pageSize) {
        // 1. 分页查询用户
        Page<SysUser> userPage = new Page<>(page, pageSize);
        QueryWrapper<SysUser> qw = new QueryWrapper<>();
        qw.eq("is_deleted", 0);

        // 角色过滤：通过角色编码查找角色ID
        if (role != null && !role.isEmpty()) {
            QueryWrapper<SysRole> roleQw = new QueryWrapper<>();
            roleQw.eq("role_code", role);
            SysRole sysRole = sysRoleMapper.selectOne(roleQw);
            if (sysRole != null) {
                qw.eq("role_id", sysRole.getId());
            }
        }

        // 关键字过滤
        if (keyword != null && !keyword.isEmpty()) {
            qw.and(w -> w.like("username", keyword)
                    .or().like("real_name", keyword)
                    .or().like("phone", keyword));
        }

        // 部门过滤
        if (department != null) {
            qw.eq("department_id", department);
        }

        // 班级过滤：通过班级名称查找班级ID
        if (className != null && !className.isEmpty()) {
            QueryWrapper<SysClass> classQw = new QueryWrapper<>();
            classQw.eq("class_name", className);
            List<SysClass> classList = sysClassMapper.selectList(classQw);
            if (!classList.isEmpty()) {
                qw.in("class_id", classList.stream().map(SysClass::getId).collect(Collectors.toList()));
            }
        }

        // 状态过滤
        if (status != null) {
            qw.eq("status", status);
        }

        qw.orderByDesc("create_time");
        IPage<SysUser> pageResult = sysUserMapper.selectPage(userPage, qw);
        List<SysUser> users = pageResult.getRecords();

        // 2. 组装结果（关联角色名、部门名、班级名）
        List<Map<String, Object>> userList = new ArrayList<>();
        for (SysUser user : users) {
            Map<String, Object> userMap = buildUserMap(user);
            userList.add(userMap);
        }

        // 3. 构建分页结果
        Page<Map<String, Object>> resultPage = new Page<>(userPage.getCurrent(), userPage.getSize());
        resultPage.setTotal(pageResult.getTotal());
        resultPage.setRecords(userList);

        return resultPage;
    }

    @Override
    public Map<String, Object> getUserDetail(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return buildUserMap(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createUser(String account, String userName, String role,
                                           String password, Long department,
                                           String email, String phone, String className) {
        // 1. 校验账号唯一性
        QueryWrapper<SysUser> existQw = new QueryWrapper<>();
        existQw.eq("username", account);
        Long count = sysUserMapper.selectCount(existQw);
        if (count != null && count > 0) {
            throw new BusinessException(400, "账号已存在：" + account);
        }

        // 2. 查找角色
        QueryWrapper<SysRole> roleQw = new QueryWrapper<>();
        roleQw.eq("role_code", role);
        SysRole sysRole = sysRoleMapper.selectOne(roleQw);
        if (sysRole == null) {
            throw new BusinessException(400, "角色编码不存在：" + role);
        }

        // 3. 查找班级（可选）
        Long classId = null;
        if (className != null && !className.isEmpty()) {
            QueryWrapper<SysClass> classQw = new QueryWrapper<>();
            classQw.eq("class_name", className);
            SysClass sysClass = sysClassMapper.selectOne(classQw);
            if (sysClass == null) {
                throw new BusinessException(400, "班级名称不存在：" + className);
            }
            classId = sysClass.getId();
        }

        // 4. 确定密码
        String finalPassword = password;
        if (finalPassword == null || finalPassword.isEmpty()) {
            // 从系统配置中读取默认密码
            QueryWrapper<SysConfig> cfgQw = new QueryWrapper<>();
            cfgQw.eq("config_key", CONFIG_DEFAULT_PASSWORD);
            SysConfig cfg = sysConfigMapper.selectOne(cfgQw);
            finalPassword = (cfg != null && cfg.getConfigValue() != null && !cfg.getConfigValue().isEmpty())
                    ? cfg.getConfigValue() : "123456";
        }

        // 5. 创建用户
        SysUser newUser = new SysUser();
        newUser.setUsername(account);
        newUser.setPassword(finalPassword);
        newUser.setRealName(userName);
        newUser.setRoleId(sysRole.getId());
        newUser.setDepartmentId(department);
        newUser.setClassId(classId);
        newUser.setEmail(email);
        newUser.setPhone(phone);
        newUser.setStatus(1); // 默认启用
        newUser.setLoginFailCount(0);

        sysUserMapper.insert(newUser);
        log.info("管理员 {} 创建了用户 {}（账号：{}）", UserContext.getUserId(), userName, account);

        return buildUserMap(newUser);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importUsers(MultipartFile file) {
        // 1. 解析 Excel 文件
        List<Map<String, String>> rows;
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            rows = parseExcelSheet(sheet);
        } catch (IOException e) {
            log.error("解析导入文件失败", e);
            throw new BusinessException(400, "文件解析失败：" + e.getMessage());
        }

        // 2. 逐行验证并入库
        int successCount = 0;
        int failCount = 0;
        List<Map<String, Object>> failDetails = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            int rowNum = i + 2; // 表头占第1行，数据从第2行开始

            try {
                String account = row.get("账号");
                String userName = row.get("姓名");
                String roleCode = row.get("角色");
                String password = row.get("密码");
                String deptName = row.get("部门");
                String email = row.get("邮箱");
                String phone = row.get("手机号");
                String clsName = row.get("班级");

                // 基本校验
                if (account == null || account.trim().isEmpty()) {
                    throw new BusinessException(400, "账号不能为空");
                }
                if (userName == null || userName.trim().isEmpty()) {
                    throw new BusinessException(400, "姓名不能为空");
                }
                if (roleCode == null || roleCode.trim().isEmpty()) {
                    throw new BusinessException(400, "角色不能为空");
                }

                // 检查账号重复（数据库已有）
                QueryWrapper<SysUser> existQw = new QueryWrapper<>();
                existQw.eq("username", account.trim());
                Long existCount = sysUserMapper.selectCount(existQw);
                if (existCount != null && existCount > 0) {
                    throw new BusinessException(400, "账号已存在");
                }

                // 查找角色
                QueryWrapper<SysRole> roleQw = new QueryWrapper<>();
                roleQw.eq("role_code", roleCode.trim());
                SysRole sysRole = sysRoleMapper.selectOne(roleQw);
                Long roleId = (sysRole != null) ? sysRole.getId() : null;
                if (roleId == null) {
                    throw new BusinessException(400, "角色编码不存在：" + roleCode);
                }

                // 查找部门
                Long deptId = null;
                if (deptName != null && !deptName.trim().isEmpty()) {
                    QueryWrapper<SysDepartment> deptQw = new QueryWrapper<>();
                    deptQw.eq("dept_name", deptName.trim());
                    SysDepartment dept = sysDepartmentMapper.selectOne(deptQw);
                    deptId = (dept != null) ? dept.getId() : null;
                }

                // 查找班级
                Long classId = null;
                if (clsName != null && !clsName.trim().isEmpty()) {
                    QueryWrapper<SysClass> classQw = new QueryWrapper<>();
                    classQw.eq("class_name", clsName.trim());
                    SysClass sysClass = sysClassMapper.selectOne(classQw);
                    classId = (sysClass != null) ? sysClass.getId() : null;
                }

                // 构建用户
                SysUser user = new SysUser();
                user.setUsername(account.trim());
                user.setRealName(userName.trim());
                user.setPassword((password != null && !password.trim().isEmpty()) ? password.trim() : "123456");
                user.setRoleId(roleId);
                user.setDepartmentId(deptId);
                user.setClassId(classId);
                user.setEmail(email != null ? email.trim() : null);
                user.setPhone(phone != null ? phone.trim() : null);
                user.setStatus(1);
                user.setLoginFailCount(0);

                sysUserMapper.insert(user);
                successCount++;

            } catch (Exception e) {
                failCount++;
                Map<String, Object> failItem = new LinkedHashMap<>();
                failItem.put("row", rowNum);
                failItem.put("reason", e.getMessage());
                failDetails.add(failItem);
                log.warn("导入第 {} 行失败：{}", rowNum, e.getMessage());
            }
        }

        // 3. 返回结果
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("successCount", successCount);
        result.put("failCount", failCount);
        result.put("failDetails", failDetails);

        log.info("管理员 {} 批量导入用户完成：成功 {} 条，失败 {} 条",
                UserContext.getUserId(), successCount, failCount);

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUser(Long userId, Map<String, Object> updates) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        // 更新可修改字段
        if (updates.containsKey("userName") || updates.containsKey("realName")) {
            String name = (String) updates.getOrDefault("userName", updates.get("realName"));
            if (name != null) user.setRealName(name);
        }
        if (updates.containsKey("department")) {
            Object deptVal = updates.get("department");
            String deptStr = deptVal != null ? deptVal.toString().trim() : "";
            if (!deptStr.isEmpty()) {
                // 尝试按 ID 解析，否则按名称查找
                try {
                    user.setDepartmentId(Long.valueOf(deptStr));
                } catch (NumberFormatException e) {
                    QueryWrapper<SysDepartment> deptQw = new QueryWrapper<>();
                    deptQw.eq("dept_name", deptStr);
                    SysDepartment dept = sysDepartmentMapper.selectOne(deptQw);
                    user.setDepartmentId(dept != null ? dept.getId() : null);
                }
            } else {
                user.setDepartmentId(null);
            }
        }
        if (updates.containsKey("className")) {
            Object classVal = updates.get("className");
            String clsName = classVal != null ? classVal.toString().trim() : "";
            if (!clsName.isEmpty()) {
                QueryWrapper<SysClass> classQw = new QueryWrapper<>();
                classQw.eq("class_name", clsName);
                SysClass sysClass = sysClassMapper.selectOne(classQw);
                user.setClassId(sysClass != null ? sysClass.getId() : null);
            } else {
                user.setClassId(null);
            }
        }
        if (updates.containsKey("email")) {
            user.setEmail((String) updates.get("email"));
        }
        if (updates.containsKey("phone")) {
            user.setPhone((String) updates.get("phone"));
        }

        sysUserMapper.updateById(user);
        log.info("管理员 {} 更新了用户 {} 的信息", UserContext.getUserId(), userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserStatus(Long userId, String status, String reason) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        // 状态映射
        Integer statusInt;
        switch (status.toUpperCase()) {
            case "ACTIVE":
                statusInt = 1;
                break;
            case "FROZEN":
                statusInt = 0; // 冻结视为禁用
                break;
            case "LOCKED":
                statusInt = 0; // 锁定也视为禁用
                user.setLockUntilTime(LocalDateTime.now().plusDays(1)); // 锁定1天
                break;
            default:
                throw new BusinessException(400, "不支持的状态值：" + status);
        }

        user.setStatus(statusInt);
        sysUserMapper.updateById(user);

        // 记录状态变更日志
        log.info("管理员 {} 变更用户 {} 状态为 {}，原因：{}", UserContext.getUserId(), userId, status, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetUserPassword(Long userId, String newPassword) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        String finalPassword = newPassword;
        if (finalPassword == null || finalPassword.isEmpty()) {
            // 使用系统默认密码
            QueryWrapper<SysConfig> cfgQw = new QueryWrapper<>();
            cfgQw.eq("config_key", CONFIG_DEFAULT_PASSWORD);
            SysConfig cfg = sysConfigMapper.selectOne(cfgQw);
            finalPassword = (cfg != null && cfg.getConfigValue() != null && !cfg.getConfigValue().isEmpty())
                    ? cfg.getConfigValue() : "123456";
        }

        user.setPassword(finalPassword);
        sysUserMapper.updateById(user);
        log.info("管理员 {} 重置了用户 {} 的密码", UserContext.getUserId(), userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long userId) {
        Long adminId = UserContext.getUserId();

        // 不允许删除自己
        if (adminId.equals(userId)) {
            throw new BusinessException(400, "不能删除自己的账号");
        }

        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        // 物理删除（绕过 @TableLogic），释放 username 供重新导入
        sysUserMapper.physicalDelete(userId);

        log.info("管理员 {} 物理删除了用户 {}（账号：{}）", adminId, user.getRealName(), user.getUsername());
    }

    @Override
    public Map<String, Object> createClass(String className, String classCode,
                                           Long departmentId, String grade) {
        if (className == null || className.trim().isEmpty()) {
            throw new BusinessException(400, "班级名称不能为空");
        }
        if (departmentId == null) {
            throw new BusinessException(400, "请选择所属学院");
        }

        // 检查班级名是否重复
        QueryWrapper<SysClass> existQw = new QueryWrapper<>();
        existQw.eq("class_name", className.trim());
        if (sysClassMapper.selectCount(existQw) > 0) {
            throw new BusinessException(400, "班级名称已存在：" + className);
        }

        SysClass newClass = new SysClass();
        newClass.setClassName(className.trim());
        newClass.setClassCode(classCode != null ? classCode.trim() : className.trim());
        newClass.setDepartmentId(departmentId);
        newClass.setGrade(grade != null ? grade.trim() : "");
        newClass.setIsDeleted(0);
        sysClassMapper.insert(newClass);

        log.info("管理员 {} 创建了班级：{}（ID：{}，院系ID：{}）",
                UserContext.getUserId(), className, newClass.getId(), departmentId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("classId", newClass.getId());
        result.put("className", className.trim());
        result.put("departmentId", departmentId);
        return result;
    }

    @Override
    public Map<String, Object> createDepartment(String deptName, String deptCode) {
        if (deptName == null || deptName.trim().isEmpty()) {
            throw new BusinessException(400, "学院名称不能为空");
        }

        // 检查名称是否重复
        QueryWrapper<SysDepartment> existQw = new QueryWrapper<>();
        existQw.eq("dept_name", deptName.trim());
        if (sysDepartmentMapper.selectCount(existQw) > 0) {
            throw new BusinessException(400, "学院名称已存在：" + deptName);
        }

        SysDepartment dept = new SysDepartment();
        dept.setDeptName(deptName.trim());
        dept.setDeptCode(deptCode != null ? deptCode.trim() : deptName.trim());
        sysDepartmentMapper.insert(dept);

        log.info("管理员 {} 创建了学院：{}（ID：{}）", UserContext.getUserId(), deptName, dept.getId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("departmentId", dept.getId());
        result.put("departmentName", deptName.trim());
        return result;
    }

    // ==================== 课程审核 ====================

    @Override
    public IPage<Map<String, Object>> getAuditCourses(String status, Long semester,
                                                       Long department,
                                                       Integer page, Integer pageSize) {
        // 1. 分页查询开课计划
        Page<CoursePlan> planPage = new Page<>(page, pageSize);
        QueryWrapper<CoursePlan> qw = new QueryWrapper<>();

        if (status != null && !status.isEmpty()) {
            qw.eq("audit_status", status);
        }
        if (semester != null) {
            qw.eq("semester_id", semester);
        }
        qw.orderByDesc("create_time");

        IPage<CoursePlan> pageResult = coursePlanMapper.selectPage(planPage, qw);
        List<CoursePlan> plans = pageResult.getRecords();

        // 2. 组装数据（关联课程、教师、班级、部门）
        List<Map<String, Object>> planList = new ArrayList<>();
        for (CoursePlan plan : plans) {
            // 课程信息
            Course course = courseMapper.selectById(plan.getCourseId());

            // 部门过滤（通过课程关联的部门）
            if (department != null && (course == null || !department.equals(course.getDepartmentId()))) {
                continue;
            }

            Map<String, Object> planMap = new LinkedHashMap<>();
            planMap.put("planId", plan.getId());
            planMap.put("courseId", plan.getCourseId());
            planMap.put("courseName", course != null ? course.getCourseName() : "");
            planMap.put("courseCode", course != null ? course.getCourseCode() : "");
            planMap.put("semesterId", plan.getSemesterId());
            planMap.put("semesterName", getSemesterName(plan.getSemesterId()));
            planMap.put("classId", plan.getClassId());
            planMap.put("className", getClassName(plan.getClassId()));
            planMap.put("teacherId", plan.getTeacherId());
            planMap.put("teacherName", getUserRealName(plan.getTeacherId()));
            planMap.put("departmentId", course != null ? course.getDepartmentId() : null);
            planMap.put("departmentName", getDepartmentName(course != null ? course.getDepartmentId() : null));
            planMap.put("submitTime", plan.getCreateTime());                                // 提交时间
            planMap.put("auditStatus", plan.getAuditStatus());
            planMap.put("auditComment", plan.getAuditComment());
            planMap.put("auditTime", plan.getAuditTime());
            planMap.put("auditAdminId", plan.getAuditAdminId());
            planMap.put("createTime", plan.getCreateTime());

            planList.add(planMap);
        }

        // 3. 构建分页结果
        Page<Map<String, Object>> resultPage = new Page<>(planPage.getCurrent(), planPage.getSize());
        resultPage.setTotal(pageResult.getTotal());
        resultPage.setRecords(planList);

        return resultPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditCourse(Long courseId, String action, String comment) {
        Long adminId = UserContext.getUserId();
        String adminName = UserContext.getUsername();

        // 1. 查询开课计划
        CoursePlan plan = coursePlanMapper.selectById(courseId);
        if (plan == null) {
            throw new BusinessException(404, "开课计划不存在");
        }

        // 2. 检查状态是否可审核
        if (!AUDIT_PENDING.equals(plan.getAuditStatus())) {
            throw new BusinessException(400, "该开课计划当前状态不允许审核（当前状态：" + plan.getAuditStatus() + "）");
        }

        // 3. 执行审核
        String beforeStatus = plan.getAuditStatus();
        String afterStatus;
        if (AUDIT_APPROVED.equals(action.toUpperCase())) {
            afterStatus = AUDIT_APPROVED;
        } else if (AUDIT_REJECTED.equals(action.toUpperCase())) {
            afterStatus = AUDIT_REJECTED;
        } else {
            throw new BusinessException(400, "审核动作无效，仅支持 APPROVED/REJECTED");
        }

        plan.setAuditStatus(afterStatus);
        plan.setAuditAdminId(adminId);
        plan.setAuditComment(comment);
        plan.setAuditTime(LocalDateTime.now());
        coursePlanMapper.updateById(plan);

        // 4. 同步更新 Course 的审核状态
        if (plan.getCourseId() != null) {
            Course course = courseMapper.selectById(plan.getCourseId());
            if (course != null) {
                course.setAuditStatus(afterStatus);
                course.setAuditAdminId(adminId);
                course.setAuditComment(comment);
                course.setAuditTime(LocalDateTime.now());
                courseMapper.updateById(course);
            }
        }

        // 5. 创建审核日志
        SysAuditLog auditLog = new SysAuditLog();
        auditLog.setBizType("COURSE_PLAN");
        auditLog.setBizId(plan.getId());
        auditLog.setBizName(getCourseName(plan.getCourseId()));
        auditLog.setAction(afterStatus);
        auditLog.setOperatorId(adminId);
        auditLog.setOperatorName(adminName);
        auditLog.setComment(comment);
        auditLog.setBeforeStatus(beforeStatus);
        auditLog.setAfterStatus(afterStatus);
        sysAuditLogMapper.insert(auditLog);

        // 6. 发送通知给授课教师
        if (plan.getTeacherId() != null) {
            SysMessage msg = new SysMessage();
            msg.setSenderId(adminId);
            msg.setReceiverId(plan.getTeacherId());
            msg.setTitle("课程审核" + ("APPROVED".equals(afterStatus) ? "通过" : "未通过"));
            msg.setContent("您的课程《" + getCourseName(plan.getCourseId()) + "》已" +
                    ("APPROVED".equals(afterStatus) ? "通过审核" : "被驳回，原因：" + (comment != null ? comment : "无")));
            msg.setMessageType("AUDIT_NOTIFICATION");
            msg.setIsRead(0);
            msg.setRelatedBizType("COURSE_PLAN");
            msg.setRelatedBizId(plan.getId());
            sysMessageMapper.insert(msg);
        }

        log.info("管理员 {} 审核课程 {}（ID：{}）结果为：{}", adminId,
                getCourseName(plan.getCourseId()), courseId, afterStatus);
    }

    @Override
    public IPage<Map<String, Object>> getAuditCourseLogs(Long courseId, Long auditorId,
                                                          LocalDate startDate, LocalDate endDate,
                                                          Integer page, Integer pageSize) {
        Page<SysAuditLog> logPage = new Page<>(page, pageSize);
        QueryWrapper<SysAuditLog> qw = new QueryWrapper<>();
        qw.in("biz_type", "COURSE_PLAN", "COURSE");

        if (courseId != null) {
            qw.eq("biz_id", courseId);
        }
        if (auditorId != null) {
            qw.eq("operator_id", auditorId);
        }
        if (startDate != null && endDate != null) {
            qw.between("create_time", startDate.atStartOfDay(), endDate.atTime(23, 59, 59));
        } else if (startDate != null) {
            qw.ge("create_time", startDate.atStartOfDay());
        } else if (endDate != null) {
            qw.le("create_time", endDate.atTime(23, 59, 59));
        }

        qw.orderByDesc("create_time");

        IPage<SysAuditLog> pageResult = sysAuditLogMapper.selectPage(logPage, qw);
        List<SysAuditLog> logs = pageResult.getRecords();

        List<Map<String, Object>> logList = new ArrayList<>();
        for (SysAuditLog al : logs) {
            // 查找关联的教师名称（通过 CoursePlan）
            String teacherName = "";
            if ("COURSE_PLAN".equals(al.getBizType()) && al.getBizId() != null) {
                CoursePlan relatedPlan = coursePlanMapper.selectById(al.getBizId());
                if (relatedPlan != null) {
                    teacherName = getUserRealName(relatedPlan.getTeacherId());
                }
            }

            // 动作简称（前端用）
            String actionShort = "";
            if (AUDIT_APPROVED.equals(al.getAction())) {
                actionShort = "APPROVE";
            } else if (AUDIT_REJECTED.equals(al.getAction())) {
                actionShort = "REJECT";
            }

            // 结果中文
            String resultCn = "";
            if (AUDIT_APPROVED.equals(al.getAction())) {
                resultCn = "通过";
            } else if (AUDIT_REJECTED.equals(al.getAction())) {
                resultCn = "驳回";
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", al.getId());
            item.put("bizType", al.getBizType());
            item.put("bizId", al.getBizId());
            item.put("bizName", al.getBizName());
            item.put("courseName", al.getBizName());       // 前端课程审核日志用
            item.put("teacherName", teacherName);           // 授课教师
            item.put("action", al.getAction());             // "APPROVED" / "REJECTED"
            item.put("actionShort", actionShort);           // "APPROVE" / "REJECT"（前端判断用）
            item.put("result", resultCn);                   // "通过" / "驳回"（前端显示用）
            item.put("operatorId", al.getOperatorId());
            item.put("operatorName", al.getOperatorName());
            item.put("operator", al.getOperatorName());     // 前端仪表盘用
            item.put("auditor", al.getOperatorName());      // 前端审核日志用
            item.put("comment", al.getComment());
            item.put("beforeStatus", al.getBeforeStatus());
            item.put("afterStatus", al.getAfterStatus());
            item.put("createTime", al.getCreateTime());
            item.put("time", al.getCreateTime());           // 前端显示用
            logList.add(item);
        }

        Page<Map<String, Object>> resultPage = new Page<>(logPage.getCurrent(), logPage.getSize());
        resultPage.setTotal(pageResult.getTotal());
        resultPage.setRecords(logList);

        return resultPage;
    }

    @Override
    public Map<String, Object> getCourseStatistics() {
        // 1. 按审核状态统计
        QueryWrapper<CoursePlan> totalQw = new QueryWrapper<>();
        long total = coursePlanMapper.selectCount(totalQw);

        QueryWrapper<CoursePlan> approvedQw = new QueryWrapper<>();
        approvedQw.eq("audit_status", AUDIT_APPROVED);
        long approved = coursePlanMapper.selectCount(approvedQw);

        QueryWrapper<CoursePlan> pendingQw = new QueryWrapper<>();
        pendingQw.eq("audit_status", AUDIT_PENDING);
        long pending = coursePlanMapper.selectCount(pendingQw);

        QueryWrapper<CoursePlan> rejectedQw = new QueryWrapper<>();
        rejectedQw.eq("audit_status", AUDIT_REJECTED);
        long rejected = coursePlanMapper.selectCount(rejectedQw);

        // 2. 按部门统计
        List<Map<String, Object>> byDepartment = new ArrayList<>();
        List<SysDepartment> departments = sysDepartmentMapper.selectList(null);
        for (SysDepartment dept : departments) {
            // 统计该部门下的课程总量和审核情况
            long deptTotal = 0;
            long deptApproved = 0;
            long deptPending = 0;

            QueryWrapper<Course> courseQw = new QueryWrapper<>();
            courseQw.eq("department_id", dept.getId());
            List<Course> deptCourses = courseMapper.selectList(courseQw);

            if (!deptCourses.isEmpty()) {
                Set<Long> courseIds = deptCourses.stream().map(Course::getId).collect(Collectors.toSet());
                QueryWrapper<CoursePlan> planQw = new QueryWrapper<>();
                planQw.in("course_id", courseIds);
                List<CoursePlan> deptPlans = coursePlanMapper.selectList(planQw);

                deptTotal = deptPlans.size();
                deptApproved = deptPlans.stream().filter(p -> AUDIT_APPROVED.equals(p.getAuditStatus())).count();
                deptPending = deptPlans.stream().filter(p -> AUDIT_PENDING.equals(p.getAuditStatus())).count();
            }

            Map<String, Object> deptStat = new LinkedHashMap<>();
            deptStat.put("departmentId", dept.getId());
            deptStat.put("departmentName", dept.getDeptName());
            deptStat.put("total", deptTotal);
            deptStat.put("approved", deptApproved);
            deptStat.put("pending", deptPending);
            byDepartment.add(deptStat);
        }

        // 3. 按学期统计
        List<Map<String, Object>> bySemester = new ArrayList<>();
        List<SysSemester> semesters = sysSemesterMapper.selectList(null);
        for (SysSemester sem : semesters) {
            QueryWrapper<CoursePlan> semQw = new QueryWrapper<>();
            semQw.eq("semester_id", sem.getId());
            List<CoursePlan> semPlans = coursePlanMapper.selectList(semQw);

            Map<String, Object> semStat = new LinkedHashMap<>();
            semStat.put("semesterId", sem.getId());
            semStat.put("semesterName", sem.getSemesterName());
            semStat.put("total", semPlans.size());
            semStat.put("approved", semPlans.stream().filter(p -> AUDIT_APPROVED.equals(p.getAuditStatus())).count());
            semStat.put("pending", semPlans.stream().filter(p -> AUDIT_PENDING.equals(p.getAuditStatus())).count());
            semStat.put("rejected", semPlans.stream().filter(p -> AUDIT_REJECTED.equals(p.getAuditStatus())).count());
            bySemester.add(semStat);
        }

        // 4. 今日审核统计（基于 SysAuditLog）
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.atTime(23, 59, 59);

        QueryWrapper<SysAuditLog> todayApprovedQw = new QueryWrapper<>();
        todayApprovedQw.eq("biz_type", "COURSE_PLAN")
                .eq("action", AUDIT_APPROVED)
                .between("create_time", todayStart, todayEnd);
        long todayApproved = sysAuditLogMapper.selectCount(todayApprovedQw);

        QueryWrapper<SysAuditLog> todayRejectedQw = new QueryWrapper<>();
        todayRejectedQw.eq("biz_type", "COURSE_PLAN")
                .eq("action", AUDIT_REJECTED)
                .between("create_time", todayStart, todayEnd);
        long todayRejected = sysAuditLogMapper.selectCount(todayRejectedQw);

        // 本周审核总数
        LocalDate weekStart = today.with(java.time.DayOfWeek.MONDAY);
        QueryWrapper<SysAuditLog> weekQw = new QueryWrapper<>();
        weekQw.eq("biz_type", "COURSE_PLAN")
                .between("create_time", weekStart.atStartOfDay(), todayEnd);
        long thisWeekTotal = sysAuditLogMapper.selectCount(weekQw);

        // 组装结果
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("approved", approved);
        result.put("pending", pending);
        result.put("rejected", rejected);
        result.put("todayApproved", todayApproved);
        result.put("todayRejected", todayRejected);
        result.put("thisWeekTotal", thisWeekTotal);
        result.put("byDepartment", byDepartment);
        result.put("bySemester", bySemester);

        return result;
    }

    // ==================== 任务审核 ====================

    @Override
    public IPage<Map<String, Object>> getAuditTasks(String status, String taskType,
                                                     Integer page, Integer pageSize) {
        Page<ExperimentTask> taskPage = new Page<>(page, pageSize);
        QueryWrapper<ExperimentTask> qw = new QueryWrapper<>();

        if (status != null && !status.isEmpty()) {
            qw.eq("audit_status", status);
        }
        qw.orderByDesc("create_time");

        IPage<ExperimentTask> pageResult = experimentTaskMapper.selectPage(taskPage, qw);
        List<ExperimentTask> tasks = pageResult.getRecords();

        List<Map<String, Object>> taskList = new ArrayList<>();
        for (ExperimentTask task : tasks) {
            // 获取项目信息，按项目类型过滤
            ExperimentProject project = experimentProjectMapper.selectById(task.getProjectId());
            if (taskType != null && !taskType.isEmpty()) {
                if (project == null || !taskType.equals(project.getProjectType())) {
                    continue;
                }
            }

            Map<String, Object> taskMap = new LinkedHashMap<>();
            taskMap.put("taskId", task.getId());
            taskMap.put("projectId", task.getProjectId());
            taskMap.put("projectName", project != null ? project.getProjectName() : "");
            taskMap.put("title", project != null ? project.getProjectName() : "");  // 前端用
            taskMap.put("projectType", project != null ? project.getProjectType() : "");
            taskMap.put("taskType", project != null ? project.getProjectType() : ""); // 前端用
            taskMap.put("classId", task.getClassId());
            taskMap.put("className", getClassName(task.getClassId()));
            taskMap.put("teacherId", task.getTeacherId());
            taskMap.put("teacherName", getUserRealName(task.getTeacherId()));
            taskMap.put("startTime", task.getStartTime());
            taskMap.put("endTime", task.getEndTime());
            taskMap.put("auditStatus", task.getAuditStatus());
            taskMap.put("auditComment", task.getAuditComment());
            taskMap.put("auditTime", task.getAuditTime());
            taskMap.put("createTime", task.getCreateTime());
            taskList.add(taskMap);
        }

        Page<Map<String, Object>> resultPage = new Page<>(taskPage.getCurrent(), taskPage.getSize());
        resultPage.setTotal(pageResult.getTotal());
        resultPage.setRecords(taskList);

        return resultPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditTask(Long taskId, String action, String comment) {
        Long adminId = UserContext.getUserId();
        String adminName = UserContext.getUsername();

        // 1. 查询任务
        ExperimentTask task = experimentTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "实验任务不存在");
        }

        // 2. 检查状态
        if (!AUDIT_PENDING.equals(task.getAuditStatus())) {
            throw new BusinessException(400, "该任务当前状态不允许审核（当前状态：" + task.getAuditStatus() + "）");
        }

        // 3. 执行审核
        String beforeStatus = task.getAuditStatus();
        String afterStatus;
        if (AUDIT_APPROVED.equals(action.toUpperCase())) {
            afterStatus = AUDIT_APPROVED;
        } else if (AUDIT_REJECTED.equals(action.toUpperCase())) {
            afterStatus = AUDIT_REJECTED;
        } else {
            throw new BusinessException(400, "审核动作无效，仅支持 APPROVED/REJECTED");
        }

        task.setAuditStatus(afterStatus);
        task.setAuditAdminId(adminId);
        task.setAuditComment(comment);
        task.setAuditTime(LocalDateTime.now());
        experimentTaskMapper.updateById(task);

        // 4. 创建审核日志
        SysAuditLog auditLog = new SysAuditLog();
        auditLog.setBizType("EXPERIMENT_TASK");
        auditLog.setBizId(task.getId());
        auditLog.setBizName(getExperimentProjectName(task.getProjectId()));
        auditLog.setAction(afterStatus);
        auditLog.setOperatorId(adminId);
        auditLog.setOperatorName(adminName);
        auditLog.setComment(comment);
        auditLog.setBeforeStatus(beforeStatus);
        auditLog.setAfterStatus(afterStatus);
        sysAuditLogMapper.insert(auditLog);

        // 5. 发送通知给教师
        if (task.getTeacherId() != null) {
            SysMessage msg = new SysMessage();
            msg.setSenderId(adminId);
            msg.setReceiverId(task.getTeacherId());
            msg.setTitle("实验任务审核" + ("APPROVED".equals(afterStatus) ? "通过" : "未通过"));
            msg.setContent("您的实验任务《" + getExperimentProjectName(task.getProjectId()) + "》已" +
                    ("APPROVED".equals(afterStatus) ? "通过审核" : "被驳回，原因：" + (comment != null ? comment : "无")));
            msg.setMessageType("AUDIT_NOTIFICATION");
            msg.setIsRead(0);
            msg.setRelatedBizType("EXPERIMENT_TASK");
            msg.setRelatedBizId(task.getId());
            sysMessageMapper.insert(msg);
        }

        log.info("管理员 {} 审核实验任务 {}（ID：{}）结果为：{}",
                adminId, getExperimentProjectName(task.getProjectId()), taskId, afterStatus);
    }

    @Override
    public Map<String, Object> getTaskStatistics() {
        // 1. 基本统计
        QueryWrapper<ExperimentTask> totalQw = new QueryWrapper<>();
        long total = experimentTaskMapper.selectCount(totalQw);

        QueryWrapper<ExperimentTask> approvedQw = new QueryWrapper<>();
        approvedQw.eq("audit_status", AUDIT_APPROVED);
        long approved = experimentTaskMapper.selectCount(approvedQw);

        QueryWrapper<ExperimentTask> pendingQw = new QueryWrapper<>();
        pendingQw.eq("audit_status", AUDIT_PENDING);
        long pending = experimentTaskMapper.selectCount(pendingQw);

        QueryWrapper<ExperimentTask> rejectedQw = new QueryWrapper<>();
        rejectedQw.eq("audit_status", AUDIT_REJECTED);
        long rejected = experimentTaskMapper.selectCount(rejectedQw);

        // 2. 按部门统计（通过班级-部门关联）
        List<Map<String, Object>> byDepartment = new ArrayList<>();
        List<SysDepartment> departments = sysDepartmentMapper.selectList(null);
        for (SysDepartment dept : departments) {
            // 找到该部门下的所有班级
            QueryWrapper<SysClass> classQw = new QueryWrapper<>();
            classQw.eq("department_id", dept.getId());
            List<SysClass> classes = sysClassMapper.selectList(classQw);

            long deptTotal = 0;
            long deptApproved = 0;
            long deptPending = 0;

            if (!classes.isEmpty()) {
                Set<Long> classIds = classes.stream().map(SysClass::getId).collect(Collectors.toSet());
                QueryWrapper<ExperimentTask> deptTaskQw = new QueryWrapper<>();
                deptTaskQw.in("class_id", classIds);
                List<ExperimentTask> deptTasks = experimentTaskMapper.selectList(deptTaskQw);

                deptTotal = deptTasks.size();
                deptApproved = deptTasks.stream().filter(t -> AUDIT_APPROVED.equals(t.getAuditStatus())).count();
                deptPending = deptTasks.stream().filter(t -> AUDIT_PENDING.equals(t.getAuditStatus())).count();
            }

            Map<String, Object> deptStat = new LinkedHashMap<>();
            deptStat.put("departmentId", dept.getId());
            deptStat.put("departmentName", dept.getDeptName());
            deptStat.put("total", deptTotal);
            deptStat.put("approved", deptApproved);
            deptStat.put("pending", deptPending);
            deptStat.put("rejected", deptTotal - deptApproved - deptPending);
            deptStat.put("passRate", deptTotal > 0
                    ? BigDecimal.valueOf(deptApproved).multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(deptTotal), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);
            byDepartment.add(deptStat);
        }

        // 3. 学生提交统计（已审核通过的任务中，学生的提交和批阅情况）
        long submissionTotal = 0;
        long submissionGraded = 0;
        QueryWrapper<StudentExperimentSubmission> subQw = new QueryWrapper<>();
        subQw.eq("status", "GRADED");
        submissionGraded = submissionMapper.selectCount(subQw);

        QueryWrapper<StudentExperimentSubmission> subTotalQw = new QueryWrapper<>();
        subTotalQw.in("status", "SUBMITTED", "GRADED", "RETURNED");
        submissionTotal = submissionMapper.selectCount(subTotalQw);

        // 4. 今日审核统计
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.atTime(23, 59, 59);

        QueryWrapper<SysAuditLog> todayApprovedQw = new QueryWrapper<>();
        todayApprovedQw.eq("biz_type", "EXPERIMENT_TASK")
                .eq("action", AUDIT_APPROVED)
                .between("create_time", todayStart, todayEnd);
        long todayApproved = sysAuditLogMapper.selectCount(todayApprovedQw);

        QueryWrapper<SysAuditLog> todayRejectedQw = new QueryWrapper<>();
        todayRejectedQw.eq("biz_type", "EXPERIMENT_TASK")
                .eq("action", AUDIT_REJECTED)
                .between("create_time", todayStart, todayEnd);
        long todayRejected = sysAuditLogMapper.selectCount(todayRejectedQw);

        // 本周审核总数
        LocalDate weekStart = today.with(java.time.DayOfWeek.MONDAY);
        QueryWrapper<SysAuditLog> weekQw = new QueryWrapper<>();
        weekQw.eq("biz_type", "EXPERIMENT_TASK")
                .between("create_time", weekStart.atStartOfDay(), todayEnd);
        long thisWeekTotal = sysAuditLogMapper.selectCount(weekQw);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("approved", approved);
        result.put("pending", pending);
        result.put("rejected", rejected);
        result.put("todayApproved", todayApproved);
        result.put("todayRejected", todayRejected);
        result.put("thisWeekTotal", thisWeekTotal);
        result.put("overallPassRate", total > 0
                ? BigDecimal.valueOf(approved).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        result.put("submissionTotal", submissionTotal);
        result.put("submissionGraded", submissionGraded);
        // 提交批阅率
        result.put("submissionRate", submissionTotal > 0
                ? BigDecimal.valueOf(submissionGraded).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(submissionTotal), 1, RoundingMode.HALF_UP).doubleValue()
                : 0);
        result.put("byDepartment", byDepartment);

        return result;
    }

    // ==================== 资源审核 ====================

    @Override
    public IPage<Map<String, Object>> getAuditResources(String status, String type,
                                                         Integer page, Integer pageSize) {
        Page<TeachingResource> resPage = new Page<>(page, pageSize);
        QueryWrapper<TeachingResource> qw = new QueryWrapper<>();

        if (status != null && !status.isEmpty()) {
            qw.eq("audit_status", status);
        }
        if (type != null && !type.isEmpty()) {
            qw.eq("file_type", type);
        }
        qw.orderByDesc("create_time");

        IPage<TeachingResource> pageResult = teachingResourceMapper.selectPage(resPage, qw);
        List<TeachingResource> resources = pageResult.getRecords();

        List<Map<String, Object>> resList = new ArrayList<>();
        for (TeachingResource res : resources) {
            String categoryName = getResourceCategoryName(res.getCategoryId());

            Map<String, Object> resMap = new LinkedHashMap<>();
            resMap.put("resourceId", res.getId());
            resMap.put("resourceName", res.getResourceName());
            resMap.put("name", res.getResourceName());           // 前端用
            resMap.put("description", res.getDescription());
            resMap.put("categoryId", res.getCategoryId());
            resMap.put("categoryName", categoryName);
            resMap.put("type", categoryName);                    // 前端用（课件/习题/视频/文档/其他）
            resMap.put("fileUrl", res.getFileUrl());
            resMap.put("fileName", res.getFileName());
            resMap.put("fileType", res.getFileType());
            resMap.put("fileSize", res.getFileSize());
            resMap.put("teacherId", res.getTeacherId());
            resMap.put("teacherName", getUserRealName(res.getTeacherId()));
            resMap.put("visibility", res.getVisibility());
            resMap.put("scope", res.getVisibility());            // 前端用
            resMap.put("courseId", res.getCourseId());
            resMap.put("courseName", getCourseName(res.getCourseId())); // 前端用
            resMap.put("viewCount", res.getViewCount());
            resMap.put("downloadCount", res.getDownloadCount());
            resMap.put("auditStatus", res.getAuditStatus());
            resMap.put("auditComment", res.getAuditComment());
            resMap.put("auditTime", res.getAuditTime());
            resMap.put("createTime", res.getCreateTime());
            resMap.put("submitTime", res.getCreateTime());       // 前端课程审核用
            resList.add(resMap);
        }

        Page<Map<String, Object>> resultPage = new Page<>(resPage.getCurrent(), resPage.getSize());
        resultPage.setTotal(pageResult.getTotal());
        resultPage.setRecords(resList);

        return resultPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditResource(Long resourceId, String action, String comment) {
        Long adminId = UserContext.getUserId();
        String adminName = UserContext.getUsername();

        // 1. 查询资源
        TeachingResource resource = teachingResourceMapper.selectById(resourceId);
        if (resource == null) {
            throw new BusinessException(404, "教学资源不存在");
        }

        // 2. 检查状态
        if (!AUDIT_PENDING.equals(resource.getAuditStatus())) {
            throw new BusinessException(400, "该资源当前状态不允许审核（当前状态：" + resource.getAuditStatus() + "）");
        }

        // 3. 执行审核
        String beforeStatus = resource.getAuditStatus();
        String afterStatus;
        if (AUDIT_APPROVED.equals(action.toUpperCase())) {
            afterStatus = AUDIT_APPROVED;
        } else if (AUDIT_REJECTED.equals(action.toUpperCase())) {
            afterStatus = AUDIT_REJECTED;
        } else {
            throw new BusinessException(400, "审核动作无效，仅支持 APPROVED/REJECTED");
        }

        resource.setAuditStatus(afterStatus);
        resource.setAuditAdminId(adminId);
        resource.setAuditComment(comment);
        resource.setAuditTime(LocalDateTime.now());
        teachingResourceMapper.updateById(resource);

        // 4. 创建审核日志
        SysAuditLog auditLog = new SysAuditLog();
        auditLog.setBizType("TEACHING_RESOURCE");
        auditLog.setBizId(resource.getId());
        auditLog.setBizName(resource.getResourceName());
        auditLog.setAction(afterStatus);
        auditLog.setOperatorId(adminId);
        auditLog.setOperatorName(adminName);
        auditLog.setComment(comment);
        auditLog.setBeforeStatus(beforeStatus);
        auditLog.setAfterStatus(afterStatus);
        sysAuditLogMapper.insert(auditLog);

        // 5. 发送通知给上传教师
        if (resource.getTeacherId() != null) {
            SysMessage msg = new SysMessage();
            msg.setSenderId(adminId);
            msg.setReceiverId(resource.getTeacherId());
            msg.setTitle("资源审核" + ("APPROVED".equals(afterStatus) ? "通过" : "未通过"));
            msg.setContent("您的教学资源《" + resource.getResourceName() + "》已" +
                    ("APPROVED".equals(afterStatus) ? "通过审核" : "被驳回，原因：" + (comment != null ? comment : "无")));
            msg.setMessageType("AUDIT_NOTIFICATION");
            msg.setIsRead(0);
            msg.setRelatedBizType("TEACHING_RESOURCE");
            msg.setRelatedBizId(resource.getId());
            sysMessageMapper.insert(msg);
        }

        log.info("管理员 {} 审核教学资源 {}（ID：{}）结果为：{}",
                adminId, resource.getResourceName(), resourceId, afterStatus);
    }

    @Override
    public Map<String, Object> getResourceStatistics() {
        // 1. 总数
        long total = teachingResourceMapper.selectCount(null);

        // 2. 审核统计
        QueryWrapper<TeachingResource> approvedQw = new QueryWrapper<>();
        approvedQw.eq("audit_status", AUDIT_APPROVED);
        long approved = teachingResourceMapper.selectCount(approvedQw);

        // 3. 总下载量和浏览量
        List<TeachingResource> allResources = teachingResourceMapper.selectList(null);
        long totalDownloads = allResources.stream()
                .mapToLong(r -> r.getDownloadCount() != null ? r.getDownloadCount() : 0)
                .sum();
        long totalViews = allResources.stream()
                .mapToLong(r -> r.getViewCount() != null ? r.getViewCount() : 0)
                .sum();

        // 4. 按类型统计
        List<Map<String, Object>> byType = new ArrayList<>();
        Map<String, List<TeachingResource>> typeGroup = allResources.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getFileType() != null ? r.getFileType() : "OTHER",
                        Collectors.toList()));
        for (Map.Entry<String, List<TeachingResource>> entry : typeGroup.entrySet()) {
            Map<String, Object> typeStat = new LinkedHashMap<>();
            typeStat.put("fileType", entry.getKey());
            typeStat.put("count", entry.getValue().size());
            typeStat.put("downloads", entry.getValue().stream()
                    .mapToLong(r -> r.getDownloadCount() != null ? r.getDownloadCount() : 0)
                    .sum());
            typeStat.put("views", entry.getValue().stream()
                    .mapToLong(r -> r.getViewCount() != null ? r.getViewCount() : 0)
                    .sum());
            byType.add(typeStat);
        }

        // 5. 热门资源 Top 10（按下载量排序）
        List<Map<String, Object>> topResources = allResources.stream()
                .sorted((a, b) -> {
                    int da = a.getDownloadCount() != null ? a.getDownloadCount() : 0;
                    int db = b.getDownloadCount() != null ? b.getDownloadCount() : 0;
                    return Integer.compare(db, da);
                })
                .limit(10)
                .map(r -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("resourceId", r.getId());
                    item.put("resourceName", r.getResourceName());
                    item.put("downloadCount", r.getDownloadCount());
                    item.put("viewCount", r.getViewCount());
                    item.put("fileType", r.getFileType());
                    return item;
                })
                .collect(Collectors.toList());

        // 6. 今日审核统计
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.atTime(23, 59, 59);

        QueryWrapper<SysAuditLog> todayApprovedQw = new QueryWrapper<>();
        todayApprovedQw.eq("biz_type", "TEACHING_RESOURCE")
                .eq("action", AUDIT_APPROVED)
                .between("create_time", todayStart, todayEnd);
        long todayApproved = sysAuditLogMapper.selectCount(todayApprovedQw);

        QueryWrapper<SysAuditLog> todayRejectedQw = new QueryWrapper<>();
        todayRejectedQw.eq("biz_type", "TEACHING_RESOURCE")
                .eq("action", AUDIT_REJECTED)
                .between("create_time", todayStart, todayEnd);
        long todayRejected = sysAuditLogMapper.selectCount(todayRejectedQw);

        // 本周审核总数
        LocalDate weekStart = today.with(java.time.DayOfWeek.MONDAY);
        QueryWrapper<SysAuditLog> weekQw = new QueryWrapper<>();
        weekQw.eq("biz_type", "TEACHING_RESOURCE")
                .between("create_time", weekStart.atStartOfDay(), todayEnd);
        long thisWeekTotal = sysAuditLogMapper.selectCount(weekQw);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("approved", approved);
        result.put("pending", total - approved);
        result.put("todayApproved", todayApproved);
        result.put("todayRejected", todayRejected);
        result.put("thisWeekTotal", thisWeekTotal);
        result.put("totalDownloads", totalDownloads);
        result.put("totalViews", totalViews);
        result.put("byType", byType);
        result.put("topResources", topResources);

        return result;
    }

    // ==================== 系统设置 ====================

    @Override
    public Map<String, Object> getSettings() {
        // 1. 读取所有系统配置
        List<SysConfig> configs = sysConfigMapper.selectList(null);
        Map<String, String> configMap = new LinkedHashMap<>();
        for (SysConfig cfg : configs) {
            configMap.put(cfg.getConfigKey(), cfg.getConfigValue());
        }

        // 2. 结构化输出（字段名与前端 settings reactive 完全对应）
        Map<String, Object> settings = new LinkedHashMap<>();

        // 当前学期/学年
        settings.put("currentSemester", configMap.getOrDefault("current_semester", ""));
        settings.put("currentSchoolYear", configMap.getOrDefault("current_school_year", ""));

        // 文件上传：前端期望 maxFileUploadSize 为数字(MB)，数据库存的是字节数
        String maxFileSizeStr = configMap.getOrDefault("max_file_upload_size", "104857600");
        try {
            long bytes = Long.parseLong(maxFileSizeStr);
            settings.put("maxFileUploadSize", (int) (bytes / (1024 * 1024))); // 转为 MB 整数
        } catch (NumberFormatException e) {
            settings.put("maxFileUploadSize", 100); // 默认 100 MB
        }

        // 允许上传格式：前端期望数组，数据库存逗号分隔字符串
        String allowedTypes = configMap.getOrDefault("allowed_file_types", "");
        if (allowedTypes != null && !allowedTypes.isEmpty()) {
            settings.put("allowedFileTypes", Arrays.asList(allowedTypes.split(",")));
        } else {
            settings.put("allowedFileTypes", new ArrayList<>());
        }

        // 学业阈值：前端期望 { excellentMinScore, goodMinScore }
        // 兼容旧 key（excellent_threshold / good_threshold）和新 key（excellent_min_score / good_min_score）
        Map<String, Object> thresholds = new LinkedHashMap<>();
        int excellentMin = parseIntOrDefault(configMap.get("excellent_min_score"),
                parseIntOrDefault(configMap.get("excellent_threshold"), 85));
        int goodMin = parseIntOrDefault(configMap.get("good_min_score"),
                parseIntOrDefault(configMap.get("good_threshold"), 70));
        thresholds.put("excellentMinScore", excellentMin);
        thresholds.put("goodMinScore", goodMin);
        settings.put("academicThresholds", thresholds);

        // 密码规则：前端期望 { defaultPassword, minLength, maxLength }
        Map<String, Object> passwordRule = new LinkedHashMap<>();
        passwordRule.put("defaultPassword", configMap.getOrDefault(CONFIG_DEFAULT_PASSWORD, "123456"));
        passwordRule.put("minLength", parseIntOrDefault(configMap.get("password_min_length"), 6));
        passwordRule.put("maxLength", parseIntOrDefault(configMap.get("password_max_length"), 20));
        settings.put("passwordRule", passwordRule);

        // 全部原始配置（备用）
        settings.put("rawConfigs", configMap);

        return settings;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSettings(Map<String, String> settingsMap) {
        if (settingsMap == null || settingsMap.isEmpty()) {
            return;
        }

        for (Map.Entry<String, String> entry : settingsMap.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            // 查找是否已存在
            QueryWrapper<SysConfig> qw = new QueryWrapper<>();
            qw.eq("config_key", key);
            SysConfig cfg = sysConfigMapper.selectOne(qw);

            if (cfg != null) {
                cfg.setConfigValue(value);
                sysConfigMapper.updateById(cfg);
            } else {
                // 不存在则新增
                cfg = new SysConfig();
                cfg.setConfigKey(key);
                cfg.setConfigValue(value);
                cfg.setDescription("由管理员在系统设置中配置");
                sysConfigMapper.insert(cfg);
            }
        }

        log.info("管理员 {} 更新了系统设置：{}", UserContext.getUserId(), settingsMap.keySet());
    }

    // ==================== 公告管理 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createAnnouncement(String title, String content, String scope,
                                                    Long department, String importance) {
        Long adminId = UserContext.getUserId();

        // 1. 创建公告
        SysAnnouncement ann = new SysAnnouncement();
        ann.setTitle(title);
        ann.setContent(content);
        ann.setPublisherId(adminId);
        ann.setStatus(1); // 已发布
        ann.setPublishTime(LocalDateTime.now());

        // 设置发布范围和目标（前端 scope: SCHOOL_WIDE / DEPARTMENT_WIDE / CLASS_ONLY）
        // 数据库 announce_type: SCHOOL / DEPARTMENT / CLASS
        if ("DEPARTMENT_WIDE".equalsIgnoreCase(scope) || SCOPE_DEPARTMENT.equalsIgnoreCase(scope)) {
            ann.setAnnounceType("DEPARTMENT");
            ann.setTargetDeptId(department);
        } else if ("CLASS_ONLY".equalsIgnoreCase(scope) || SCOPE_CLASS.equalsIgnoreCase(scope)) {
            ann.setAnnounceType("CLASS");
            ann.setTargetDeptId(department);
        } else {
            // SCHOOL_WIDE 或其他
            ann.setAnnounceType("SCHOOL");
        }

        // 重要程度映射到是否置顶
        if ("URGENT".equalsIgnoreCase(importance)) {
            ann.setIsTop(1);
        } else {
            ann.setIsTop(0);
        }

        sysAnnouncementMapper.insert(ann);
        log.info("管理员 {} 发布了公告：{}", adminId, title);

        // 2. 返回结果
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", ann.getId());
        result.put("title", ann.getTitle());
        result.put("content", ann.getContent());
        result.put("scope", scope);
        result.put("importance", importance);
        result.put("publishTime", ann.getPublishTime());

        return result;
    }

    @Override
    public IPage<Map<String, Object>> getAnnouncements(String scope, String keyword,
                                                        LocalDate startDate, LocalDate endDate,
                                                        Integer page, Integer pageSize) {
        Page<SysAnnouncement> annPage = new Page<>(page, pageSize);
        QueryWrapper<SysAnnouncement> qw = new QueryWrapper<>();
        qw.eq("is_deleted", 0);

        if (scope != null && !scope.isEmpty()) {
            // 前端 scope 值: SCHOOL_WIDE / DEPARTMENT_WIDE / CLASS_ONLY
            // 数据库 announce_type: SCHOOL / DEPARTMENT / CLASS
            if ("DEPARTMENT_WIDE".equalsIgnoreCase(scope) || SCOPE_DEPARTMENT.equalsIgnoreCase(scope)) {
                qw.eq("announce_type", "DEPARTMENT");
            } else if ("CLASS_ONLY".equalsIgnoreCase(scope) || SCOPE_CLASS.equalsIgnoreCase(scope)) {
                qw.eq("announce_type", "CLASS");
            } else {
                // SCHOOL_WIDE 或其他 → 全校
                qw.eq("announce_type", "SCHOOL");
            }
        }

        if (keyword != null && !keyword.isEmpty()) {
            qw.and(w -> w.like("title", keyword)
                    .or().like("content", keyword));
        }

        if (startDate != null && endDate != null) {
            qw.between("publish_time", startDate.atStartOfDay(), endDate.atTime(23, 59, 59));
        } else if (startDate != null) {
            qw.ge("publish_time", startDate.atStartOfDay());
        } else if (endDate != null) {
            qw.le("publish_time", endDate.atTime(23, 59, 59));
        }

        qw.orderByDesc("is_top").orderByDesc("publish_time");

        IPage<SysAnnouncement> pageResult = sysAnnouncementMapper.selectPage(annPage, qw);
        List<SysAnnouncement> announcements = pageResult.getRecords();

        List<Map<String, Object>> annList = new ArrayList<>();
        for (SysAnnouncement ann : announcements) {
            // announceType 映射为前端 scope 值
            String announceType = ann.getAnnounceType();
            String scopeValue = "SCHOOL".equals(announceType) ? "SCHOOL_WIDE"
                    : "DEPARTMENT".equals(announceType) ? "DEPARTMENT_WIDE"
                    : "CLASS".equals(announceType) ? "CLASS_ONLY" : announceType;
            // importance: isTop=1 → IMPORTANT（前端值）, else → NORMAL
            String importance = (ann.getIsTop() != null && ann.getIsTop() == 1) ? "IMPORTANT" : "NORMAL";

            Map<String, Object> annMap = new LinkedHashMap<>();
            annMap.put("id", ann.getId());
            annMap.put("announcementId", ann.getId());               // 前端用
            annMap.put("title", ann.getTitle());
            annMap.put("content", ann.getContent());
            annMap.put("announceType", announceType);
            annMap.put("scope", scopeValue);                         // 前端用
            annMap.put("targetDeptId", ann.getTargetDeptId());
            annMap.put("targetDepartmentName", getDepartmentName(ann.getTargetDeptId()));
            annMap.put("department", getDepartmentName(ann.getTargetDeptId())); // 前端用
            annMap.put("publisherId", ann.getPublisherId());
            annMap.put("publisherName", getUserRealName(ann.getPublisherId()));
            annMap.put("publisher", getUserRealName(ann.getPublisherId()));     // 前端用
            annMap.put("isTop", ann.getIsTop());
            annMap.put("importance", importance);                    // 前端用
            annMap.put("status", ann.getStatus());
            annMap.put("publishTime", ann.getPublishTime());
            annMap.put("createTime", ann.getCreateTime());
            annList.add(annMap);
        }

        Page<Map<String, Object>> resultPage = new Page<>(annPage.getCurrent(), annPage.getSize());
        resultPage.setTotal(pageResult.getTotal());
        resultPage.setRecords(annList);

        return resultPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAnnouncement(Long announcementId, Map<String, Object> updates) {
        SysAnnouncement ann = sysAnnouncementMapper.selectById(announcementId);
        if (ann == null) {
            throw new BusinessException(404, "公告不存在");
        }

        if (updates.containsKey("title")) {
            ann.setTitle((String) updates.get("title"));
        }
        if (updates.containsKey("content")) {
            ann.setContent((String) updates.get("content"));
        }
        if (updates.containsKey("importance")) {
            String importance = (String) updates.get("importance");
            ann.setIsTop("URGENT".equalsIgnoreCase(importance) ? 1 : 0);
        }

        sysAnnouncementMapper.updateById(ann);
        log.info("管理员 {} 更新了公告 {}", UserContext.getUserId(), announcementId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAnnouncement(Long announcementId) {
        SysAnnouncement ann = sysAnnouncementMapper.selectById(announcementId);
        if (ann == null) {
            throw new BusinessException(404, "公告不存在");
        }

        sysAnnouncementMapper.deleteById(announcementId);
        log.info("管理员 {} 删除了公告 {}", UserContext.getUserId(), announcementId);
    }

    // ==================== 全局学情分析 ====================

    @Override
    public Map<String, Object> getOverview(Long semester) {
        // 1. 确定学期
        Long semId = semester;
        if (semId == null) {
            // 获取当前学期
            QueryWrapper<SysSemester> semQw = new QueryWrapper<>();
            semQw.eq("is_current", 1);
            SysSemester currentSem = sysSemesterMapper.selectOne(semQw);
            if (currentSem != null) {
                semId = currentSem.getId();
            }
        }

        SysSemester targetSem = (semId != null) ? sysSemesterMapper.selectById(semId) : null;

        // 2. 用户统计（管理员工作台用）
        Long studentRoleId = getRoleIdByCode("STUDENT");
        Long teacherRoleId = getRoleIdByCode("TEACHER");
        Long adminRoleId = getRoleIdByCode("ADMIN");

        // 用户总数（非删除）
        QueryWrapper<SysUser> allUserQw = new QueryWrapper<>();
        allUserQw.eq("is_deleted", 0);
        long totalUsers = sysUserMapper.selectCount(allUserQw);

        // 学生总数
        long totalStudents = 0;
        long activeStudents = 0;
        if (studentRoleId != null) {
            QueryWrapper<SysUser> studentQw = new QueryWrapper<>();
            studentQw.eq("role_id", studentRoleId).eq("is_deleted", 0);
            totalStudents = sysUserMapper.selectCount(studentQw);

            QueryWrapper<SysUser> activeStudentQw = new QueryWrapper<>();
            activeStudentQw.eq("role_id", studentRoleId).eq("status", 1).eq("is_deleted", 0);
            activeStudents = sysUserMapper.selectCount(activeStudentQw);
        }

        // 教师总数
        long teachers = 0;
        if (teacherRoleId != null) {
            QueryWrapper<SysUser> teacherQw = new QueryWrapper<>();
            teacherQw.eq("role_id", teacherRoleId).eq("is_deleted", 0);
            teachers = sysUserMapper.selectCount(teacherQw);
        }

        // 管理员总数
        long admins = 0;
        if (adminRoleId != null) {
            QueryWrapper<SysUser> adminQw = new QueryWrapper<>();
            adminQw.eq("role_id", adminRoleId).eq("is_deleted", 0);
            admins = sysUserMapper.selectCount(adminQw);
        }

        // 冻结/禁用账号数
        QueryWrapper<SysUser> frozenQw = new QueryWrapper<>();
        frozenQw.eq("status", 0).eq("is_deleted", 0);
        long frozenAccounts = sysUserMapper.selectCount(frozenQw);

        // 待审核总数（课程计划 + 实验任务 + 教学资源）
        QueryWrapper<CoursePlan> pendingCourseQw = new QueryWrapper<>();
        pendingCourseQw.eq("audit_status", AUDIT_PENDING);
        long pendingCourses = coursePlanMapper.selectCount(pendingCourseQw);

        QueryWrapper<ExperimentTask> pendingTaskQw = new QueryWrapper<>();
        pendingTaskQw.eq("audit_status", AUDIT_PENDING);
        long pendingTasks = experimentTaskMapper.selectCount(pendingTaskQw);

        QueryWrapper<TeachingResource> pendingResQw = new QueryWrapper<>();
        pendingResQw.eq("audit_status", AUDIT_PENDING);
        long pendingResources = teachingResourceMapper.selectCount(pendingResQw);

        long pendingAudits = pendingCourses + pendingTasks + pendingResources;

        // 教学资源总数
        long totalResources = teachingResourceMapper.selectCount(null);

        // 3. 课程总数
        // 全部课程计划数（所有学期）
        long totalCourses = coursePlanMapper.selectCount(null);

        // 指定学期下的开课计划数（用于学情分析）
        long semesterCourses = 0;
        if (semId != null) {
            QueryWrapper<CoursePlan> courseQw = new QueryWrapper<>();
            courseQw.eq("semester_id", semId);
            semesterCourses = coursePlanMapper.selectCount(courseQw);
        }

        // 4. 平均完成率和通过率（从成绩表中计算）
        BigDecimal overallCompletionRate = BigDecimal.ZERO;
        BigDecimal overallPassRate = BigDecimal.ZERO;
        if (semId != null) {
            QueryWrapper<StudentGrade> gradeQw = new QueryWrapper<>();
            gradeQw.eq("semester_id", semId)
                    .eq("is_published", 1);
            List<StudentGrade> grades = studentGradeMapper.selectList(gradeQw);
            long totalGradeCount = grades.size();
            long passCount = grades.stream()
                    .filter(g -> g.getFinalGrade() != null && g.getFinalGrade().compareTo(BigDecimal.valueOf(60)) >= 0)
                    .count();
            long completedCount = grades.stream()
                    .filter(g -> g.getFinalGrade() != null)
                    .count();

            if (totalGradeCount > 0) {
                overallCompletionRate = BigDecimal.valueOf(completedCount)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(totalGradeCount), 2, RoundingMode.HALF_UP);
                overallPassRate = BigDecimal.valueOf(passCount)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(totalGradeCount), 2, RoundingMode.HALF_UP);
            }
        }

        // 5. 等级分布（从 sys_config 表读取阈值）
        int excellentThreshold = getConfigInt("excellent_min_score",
                getConfigInt("excellent_threshold", 85));
        int goodThreshold = getConfigInt("good_min_score",
                getConfigInt("good_threshold", 70));

        Map<String, Long> levelDistribution = new LinkedHashMap<>();
        levelDistribution.put("excellent", 0L); // >= excellentThreshold
        levelDistribution.put("good", 0L);      // >= goodThreshold
        levelDistribution.put("pass", 0L);      // >= 60
        levelDistribution.put("fail", 0L);      // < 60

        if (semId != null) {
            QueryWrapper<StudentGrade> allGradeQw = new QueryWrapper<>();
            allGradeQw.eq("semester_id", semId)
                    .eq("is_published", 1);
            List<StudentGrade> allGrades = studentGradeMapper.selectList(allGradeQw);
            for (StudentGrade g : allGrades) {
                if (g.getFinalGrade() == null) continue;
                int score = g.getFinalGrade().intValue();
                if (score >= excellentThreshold) {
                    levelDistribution.put("excellent", levelDistribution.get("excellent") + 1);
                } else if (score >= goodThreshold) {
                    levelDistribution.put("good", levelDistribution.get("good") + 1);
                } else if (score >= 60) {
                    levelDistribution.put("pass", levelDistribution.get("pass") + 1);
                } else {
                    levelDistribution.put("fail", levelDistribution.get("fail") + 1);
                }
            }
        }

        // 6. 按部门统计
        List<Map<String, Object>> byDepartment = new ArrayList<>();
        List<SysDepartment> departments = sysDepartmentMapper.selectList(null);
        for (SysDepartment dept : departments) {
            // 统计该部门人数
            long deptStudents = 0;
            if (studentRoleId != null) {
                QueryWrapper<SysUser> deptStudentQw = new QueryWrapper<>();
                deptStudentQw.eq("role_id", studentRoleId)
                        .eq("department_id", dept.getId());
                deptStudents = sysUserMapper.selectCount(deptStudentQw);
            }

            // 统计该部门课程数量
            long deptCourses = 0;
            if (semId != null) {
                QueryWrapper<Course> deptCourseQw = new QueryWrapper<>();
                deptCourseQw.eq("department_id", dept.getId());
                List<Course> deptCourseList = courseMapper.selectList(deptCourseQw);
                if (!deptCourseList.isEmpty()) {
                    Set<Long> courseIds = deptCourseList.stream().map(Course::getId).collect(Collectors.toSet());
                    QueryWrapper<CoursePlan> deptPlanQw = new QueryWrapper<>();
                    deptPlanQw.in("course_id", courseIds)
                            .eq("semester_id", semId);
                    deptCourses = coursePlanMapper.selectCount(deptPlanQw);
                }
            }

            Map<String, Object> deptStat = new LinkedHashMap<>();
            deptStat.put("departmentId", dept.getId());
            deptStat.put("departmentName", dept.getDeptName());
            deptStat.put("department", dept.getDeptName());          // 前端用
            deptStat.put("studentCount", deptStudents);
            deptStat.put("courseCount", deptCourses);

            // 计算该院系的完成率和通过率（从成绩表）
            BigDecimal deptCompletionRate = BigDecimal.ZERO;
            BigDecimal deptPassRate = BigDecimal.ZERO;
            if (semId != null && deptStudents > 0 && studentRoleId != null) {
                QueryWrapper<SysUser> deptStudentIdsQw = new QueryWrapper<>();
                deptStudentIdsQw.eq("role_id", studentRoleId)
                        .eq("department_id", dept.getId())
                        .eq("is_deleted", 0);
                List<SysUser> deptStudentList = sysUserMapper.selectList(deptStudentIdsQw);
                if (!deptStudentList.isEmpty()) {
                    Set<Long> deptStudentIds = deptStudentList.stream()
                            .map(SysUser::getId).collect(Collectors.toSet());
                    QueryWrapper<StudentGrade> deptGradeQw = new QueryWrapper<>();
                    deptGradeQw.in("student_id", deptStudentIds)
                            .eq("semester_id", semId)
                            .eq("is_published", 1);
                    List<StudentGrade> deptGrades = studentGradeMapper.selectList(deptGradeQw);
                    if (!deptGrades.isEmpty()) {
                        long completedCount = deptGrades.stream()
                                .filter(g -> g.getFinalGrade() != null).count();
                        long passCount = deptGrades.stream()
                                .filter(g -> g.getFinalGrade() != null
                                        && g.getFinalGrade().compareTo(BigDecimal.valueOf(60)) >= 0)
                                .count();
                        deptCompletionRate = BigDecimal.valueOf(completedCount)
                                .multiply(BigDecimal.valueOf(100))
                                .divide(BigDecimal.valueOf(deptGrades.size()), 2, RoundingMode.HALF_UP);
                        deptPassRate = BigDecimal.valueOf(passCount)
                                .multiply(BigDecimal.valueOf(100))
                                .divide(BigDecimal.valueOf(deptGrades.size()), 2, RoundingMode.HALF_UP);
                    }
                }
            }
            deptStat.put("completionRate", deptCompletionRate.doubleValue());
            deptStat.put("passRate", deptPassRate.doubleValue());
            byDepartment.add(deptStat);
        }

        // 7. 月度趋势（最近12个月的平均分和完成率）
        List<Map<String, Object>> trendData = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 11; i >= 0; i--) {
            LocalDate monthStart = today.minusMonths(i).withDayOfMonth(1);
            LocalDate monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());

            // 查询该月内完成的成绩记录
            QueryWrapper<StudentGrade> monthGradeQw = new QueryWrapper<>();
            monthGradeQw.between("create_time", monthStart.atStartOfDay(), monthEnd.atTime(23, 59, 59))
                    .eq("is_published", 1);
            List<StudentGrade> monthGrades = studentGradeMapper.selectList(monthGradeQw);

            BigDecimal monthAvgScore = BigDecimal.ZERO;
            BigDecimal monthCompletionRate = BigDecimal.ZERO;
            if (!monthGrades.isEmpty()) {
                long monthCompleted = monthGrades.stream()
                        .filter(g -> g.getFinalGrade() != null).count();
                BigDecimal scoreSum = monthGrades.stream()
                        .filter(g -> g.getFinalGrade() != null)
                        .map(StudentGrade::getFinalGrade)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                if (monthCompleted > 0) {
                    monthAvgScore = scoreSum.divide(BigDecimal.valueOf(monthCompleted), 2, RoundingMode.HALF_UP);
                    monthCompletionRate = BigDecimal.valueOf(monthCompleted)
                            .multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(monthGrades.size()), 2, RoundingMode.HALF_UP);
                }
            }

            Map<String, Object> monthData = new LinkedHashMap<>();
            monthData.put("month", monthStart.format(DateTimeFormatter.ofPattern("yyyy-MM")));
            monthData.put("avgScore", monthAvgScore.doubleValue());
            monthData.put("completionRate", monthCompletionRate.doubleValue());
            trendData.add(monthData);
        }

        // 8. 本周审核趋势（本周一到周日，每天的提交/通过/驳回数）
        List<Map<String, Object>> weeklyAuditTrend = new ArrayList<>();
        LocalDate weekStart = today.with(java.time.DayOfWeek.MONDAY);
        String[] dayLabels = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        for (int i = 0; i < 7; i++) {
            LocalDate day = weekStart.plusDays(i);
            LocalDateTime dayStart = day.atStartOfDay();
            LocalDateTime dayEnd = day.atTime(23, 59, 59);

            // 当天提交数（before_status = PENDING 的审核日志）
            QueryWrapper<SysAuditLog> submittedQw = new QueryWrapper<>();
            submittedQw.between("create_time", dayStart, dayEnd)
                    .eq("before_status", "PENDING");
            long submitted = sysAuditLogMapper.selectCount(submittedQw);

            // 当天通过数
            QueryWrapper<SysAuditLog> approvedQw = new QueryWrapper<>();
            approvedQw.between("create_time", dayStart, dayEnd)
                    .eq("action", AUDIT_APPROVED);
            long approved = sysAuditLogMapper.selectCount(approvedQw);

            // 当天驳回数
            QueryWrapper<SysAuditLog> rejectedQw = new QueryWrapper<>();
            rejectedQw.between("create_time", dayStart, dayEnd)
                    .eq("action", AUDIT_REJECTED);
            long rejected = sysAuditLogMapper.selectCount(rejectedQw);

            Map<String, Object> dayData = new LinkedHashMap<>();
            dayData.put("day", dayLabels[i]);
            dayData.put("submitted", submitted);
            dayData.put("approved", approved);
            dayData.put("rejected", rejected);
            weeklyAuditTrend.add(dayData);
        }

        // 组装结果
        Map<String, Object> result = new LinkedHashMap<>();
        // 管理员工作台统计字段
        result.put("totalUsers", totalUsers);
        result.put("totalStudents", totalStudents);
        result.put("activeStudents", activeStudents);
        result.put("teachers", teachers);
        result.put("admins", admins);
        result.put("frozenAccounts", frozenAccounts);
        result.put("pendingAudits", pendingAudits);
        result.put("pendingCourses", pendingCourses);
        result.put("pendingTasks", pendingTasks);
        result.put("pendingResources", pendingResources);
        result.put("totalCourses", totalCourses);
        result.put("semesterCourses", semesterCourses);
        result.put("totalResources", totalResources);
        // 学院与班级统计
        result.put("deptCount", departments.size());
        result.put("classCount", sysClassMapper.selectCount(null));
        // 学情分析字段
        result.put("overallCompletionRate", overallCompletionRate.doubleValue());
        result.put("overallPassRate", overallPassRate.doubleValue());
        result.put("levelDistribution", levelDistribution);
        result.put("byDepartment", byDepartment);
        result.put("deptStats", byDepartment);                       // 前端用别名
        result.put("trendData", trendData);
        result.put("weeklyAuditTrend", weeklyAuditTrend);
        result.put("semesterId", semId);
        result.put("semesterName", targetSem != null ? targetSem.getSemesterName() : "");

        return result;
    }

    @Override
    public Map<String, Object> getWarnings(Long department, String riskLevel,
                                            String warningType, Integer page, Integer pageSize) {
        // 1. 先查询所有学生的诊断记录进行汇总
        QueryWrapper<AcademicDiagnosis> summaryQw = new QueryWrapper<>();
        if (department != null) {
            // 通过部门过滤学生
            Long studentRoleId = getRoleIdByCode("STUDENT");
            if (studentRoleId != null) {
                QueryWrapper<SysUser> deptStudentQw = new QueryWrapper<>();
                deptStudentQw.eq("role_id", studentRoleId)
                        .eq("department_id", department);
                List<SysUser> deptStudents = sysUserMapper.selectList(deptStudentQw);
                if (!deptStudents.isEmpty()) {
                    summaryQw.in("student_id",
                            deptStudents.stream().map(SysUser::getId).collect(Collectors.toList()));
                }
            }
        }

        List<AcademicDiagnosis> allDiagnoses = academicDiagnosisMapper.selectList(summaryQw);

        // 汇总各等级人数
        long highRisk = allDiagnoses.stream()
                .filter(d -> "高危".equals(d.getDiagnosisLevel()) || "HIGH".equalsIgnoreCase(d.getDiagnosisLevel()))
                .count();
        long mediumRisk = allDiagnoses.stream()
                .filter(d -> "中危".equals(d.getDiagnosisLevel()) || "MEDIUM".equalsIgnoreCase(d.getDiagnosisLevel()))
                .count();
        long lowRisk = allDiagnoses.stream()
                .filter(d -> "低危".equals(d.getDiagnosisLevel()) || "LOW".equalsIgnoreCase(d.getDiagnosisLevel()))
                .count();

        // 2. 分页查询预警学生列表，使用诊断记录或成绩数据
        Page<AcademicDiagnosis> diagPage = new Page<>(page, pageSize);
        QueryWrapper<AcademicDiagnosis> diagQw = new QueryWrapper<>();

        if (riskLevel != null && !riskLevel.isEmpty()) {
            String levelCn;
            switch (riskLevel.toUpperCase()) {
                case "HIGH":
                    levelCn = "高危";
                    break;
                case "MEDIUM":
                    levelCn = "中危";
                    break;
                case "LOW":
                    levelCn = "低危";
                    break;
                default:
                    levelCn = riskLevel;
            }
            diagQw.eq("diagnosis_level", levelCn);
        }

        // 按创建时间降序
        diagQw.orderByDesc("generated_time");

        IPage<AcademicDiagnosis> diagPageResult = academicDiagnosisMapper.selectPage(diagPage, diagQw);
        List<AcademicDiagnosis> diagnoses = diagPageResult.getRecords();

        List<Map<String, Object>> warningList = new ArrayList<>();
        for (AcademicDiagnosis diag : diagnoses) {
            SysUser student = sysUserMapper.selectById(diag.getStudentId());
            if (student == null) continue;

            // 部门过滤
            if (department != null && !department.equals(student.getDepartmentId())) {
                continue;
            }

            Map<String, Object> warnItem = new LinkedHashMap<>();
            warnItem.put("studentId", diag.getStudentId());
            warnItem.put("studentName", student.getRealName());
            warnItem.put("userName", student.getRealName());          // 前端用
            warnItem.put("studentAccount", student.getUsername());
            warnItem.put("account", student.getUsername());           // 前端用
            warnItem.put("departmentId", student.getDepartmentId());
            warnItem.put("departmentName", getDepartmentName(student.getDepartmentId()));
            warnItem.put("department", getDepartmentName(student.getDepartmentId())); // 前端用
            warnItem.put("classId", student.getClassId());
            warnItem.put("className", getClassName(student.getClassId()));
            warnItem.put("riskLevel", diag.getDiagnosisLevel());
            warnItem.put("avgScore", diag.getAvgExerciseAccuracy());
            warnItem.put("taskCompletionRate", diag.getTaskCompletionRate());
            warnItem.put("totalStudyHours", diag.getTotalStudyHours());
            warnItem.put("diagnosisReport", diag.getDiagnosisReport());
            warnItem.put("suggestions", diag.getRecommendResources());
            warnItem.put("generatedTime", diag.getGeneratedTime());
            warningList.add(warnItem);
        }

        // 3. 组装结果
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", warningList);
        result.put("total", diagPageResult.getTotal());
        result.put("page", page);
        result.put("pageSize", pageSize);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("highRisk", highRisk);
        summary.put("mediumRisk", mediumRisk);
        summary.put("lowRisk", lowRisk);
        result.put("summary", summary);

        return result;
    }

    @Override
    public String exportReport(Long semester, Long department, String format) {
        // 1. 获取学情总览数据
        Map<String, Object> overview = getOverview(semester);

        // 2. 获取预警数据
        Map<String, Object> warningsData = getWarnings(department, null, null, 1, Integer.MAX_VALUE);

        StringBuilder csv = new StringBuilder();
        csv.append("﻿"); // UTF-8 BOM for Excel

        // 标题
        csv.append("全局学情分析报表\n");
        csv.append("生成时间,").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");

        // 总览统计
        csv.append("=== 全校总览 ===\n");
        csv.append("指标,数值\n");
        csv.append("全校学生,").append(overview.getOrDefault("totalStudents", 0)).append("\n");
        csv.append("开设课程,").append(overview.getOrDefault("totalCourses", 0)).append("\n");
        csv.append("完成率,").append(overview.getOrDefault("overallCompletionRate", 0)).append("%\n");
        csv.append("通过率,").append(overview.getOrDefault("overallPassRate", 0)).append("%\n\n");

        // 院系统计
        csv.append("=== 院系统计 ===\n");
        csv.append("院系,学生数,课程数,完成率,通过率\n");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> deptList = (List<Map<String, Object>>) overview.getOrDefault("byDepartment", Collections.emptyList());
        for (Map<String, Object> d : deptList) {
            csv.append(d.getOrDefault("departmentName", "")).append(",");
            csv.append(d.getOrDefault("studentCount", 0)).append(",");
            csv.append(d.getOrDefault("courseCount", 0)).append(",");
            csv.append(d.getOrDefault("completionRate", 0)).append("%,");
            csv.append(d.getOrDefault("passRate", 0)).append("%\n");
        }
        csv.append("\n");

        // 预警名单
        csv.append("=== 预警名单 ===\n");
        csv.append("姓名,账号,院系,班级,风险等级,均分,任务完成率\n");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> warningList = (List<Map<String, Object>>) warningsData.getOrDefault("records", Collections.emptyList());
        for (Map<String, Object> w : warningList) {
            csv.append(w.getOrDefault("studentName", "")).append(",");
            csv.append(w.getOrDefault("studentAccount", "")).append(",");
            csv.append(w.getOrDefault("departmentName", "")).append(",");
            csv.append(w.getOrDefault("className", "")).append(",");
            csv.append(w.getOrDefault("riskLevel", "")).append(",");
            csv.append(w.getOrDefault("avgScore", "")).append(",");
            csv.append(w.getOrDefault("taskCompletionRate", "")).append("\n");
        }

        return csv.toString();
    }

    // ==================== 按课程统计学情 ====================

    @Override
    public Map<String, Object> getCourseAnalytics(Long semester) {
        // 1. 确定学期
        Long semId = semester;
        if (semId == null) {
            QueryWrapper<SysSemester> semQw = new QueryWrapper<>();
            semQw.eq("is_current", 1);
            SysSemester currentSem = sysSemesterMapper.selectOne(semQw);
            if (currentSem != null) {
                semId = currentSem.getId();
            }
        }

        // 2. 查询该学期所有已通过的 CoursePlan
        QueryWrapper<CoursePlan> planQw = new QueryWrapper<>();
        planQw.eq("semester_id", semId)
                .eq("audit_status", AUDIT_APPROVED);
        List<CoursePlan> plans = coursePlanMapper.selectList(planQw);

        if (plans.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("courses", Collections.emptyList());
            empty.put("totalStudents", 0);
            empty.put("totalGraded", 0);
            empty.put("overallCompletionRate", 0);
            empty.put("overallPassRate", 0);
            return empty;
        }

        // 3. 为每个 CoursePlan 计算统计
        List<Map<String, Object>> planStatsList = new ArrayList<>();
        long allEnrolled = 0;
        long allCompleted = 0;
        long allGraded = 0;
        long allPassed = 0;

        for (CoursePlan plan : plans) {
            // 选课人数
            QueryWrapper<StudentCourseEnrollment> enrollQw = new QueryWrapper<>();
            enrollQw.eq("course_plan_id", plan.getId());
            long enrolled = enrollmentMapper.selectCount(enrollQw);

            // 完成人数
            QueryWrapper<StudentCourseEnrollment> completedQw = new QueryWrapper<>();
            completedQw.eq("course_plan_id", plan.getId())
                    .eq("is_completed", 1);
            long completed = enrollmentMapper.selectCount(completedQw);

            // 已发布成绩数
            QueryWrapper<StudentGrade> gradeQw = new QueryWrapper<>();
            gradeQw.eq("course_plan_id", plan.getId())
                    .eq("is_published", 1);
            List<StudentGrade> grades = studentGradeMapper.selectList(gradeQw);
            long gradedCount = grades.size();

            // 通过人数（finalGrade >= 60）
            long passedCount = grades.stream()
                    .filter(g -> g.getFinalGrade() != null
                            && g.getFinalGrade().compareTo(BigDecimal.valueOf(60)) >= 0)
                    .count();

            BigDecimal completionRate = enrolled > 0
                    ? BigDecimal.valueOf(completed).multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(enrolled), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            BigDecimal passRate = gradedCount > 0
                    ? BigDecimal.valueOf(passedCount).multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(gradedCount), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            allEnrolled += enrolled;
            allCompleted += completed;
            allGraded += gradedCount;
            allPassed += passedCount;

            Course course = courseMapper.selectById(plan.getCourseId());

            Map<String, Object> ps = new LinkedHashMap<>();
            ps.put("planId", plan.getId());
            ps.put("courseId", plan.getCourseId());
            ps.put("courseName", course != null ? course.getCourseName() : "");
            ps.put("courseCode", course != null ? course.getCourseCode() : "");
            ps.put("className", getClassName(plan.getClassId()));
            ps.put("teacherName", getUserRealName(plan.getTeacherId()));
            ps.put("studentCount", enrolled);
            ps.put("completedCount", completed);
            ps.put("completionRate", completionRate.doubleValue());
            ps.put("gradedCount", gradedCount);
            ps.put("passedCount", passedCount);
            ps.put("passRate", passRate.doubleValue());
            planStatsList.add(ps);
        }

        // 4. 按 courseId 聚合（同一课程多个班级合并）
        Map<Long, List<Map<String, Object>>> groupedByCourse = planStatsList.stream()
                .collect(Collectors.groupingBy(p -> (Long) p.get("courseId")));

        List<Map<String, Object>> courseList = new ArrayList<>();
        for (Map.Entry<Long, List<Map<String, Object>>> entry : groupedByCourse.entrySet()) {
            List<Map<String, Object>> plansForCourse = entry.getValue();

            // 汇总
            long cStudents = plansForCourse.stream().mapToLong(p -> (long) p.get("studentCount")).sum();
            long cCompleted = plansForCourse.stream().mapToLong(p -> (long) p.get("completedCount")).sum();
            long cGraded = plansForCourse.stream().mapToLong(p -> (long) p.get("gradedCount")).sum();
            long cPassed = plansForCourse.stream().mapToLong(p -> (long) p.get("passedCount")).sum();

            BigDecimal cCompletion = cStudents > 0
                    ? BigDecimal.valueOf(cCompleted).multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(cStudents), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            BigDecimal cPass = cGraded > 0
                    ? BigDecimal.valueOf(cPassed).multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(cGraded), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            Map<String, Object> first = plansForCourse.get(0);
            Map<String, Object> courseItem = new LinkedHashMap<>();
            courseItem.put("courseId", first.get("courseId"));
            courseItem.put("courseName", first.get("courseName"));
            courseItem.put("courseCode", first.get("courseCode"));
            courseItem.put("planCount", plansForCourse.size());
            courseItem.put("studentCount", cStudents);
            courseItem.put("completedCount", cCompleted);
            courseItem.put("completionRate", cCompletion.doubleValue());
            courseItem.put("gradedCount", cGraded);
            courseItem.put("passedCount", cPassed);
            courseItem.put("passRate", cPass.doubleValue());
            // 附下级明细
            courseItem.put("plans", plansForCourse);
            courseList.add(courseItem);
        }

        // 按完成率降序
        courseList.sort((a, b) -> Double.compare(
                (double) b.get("completionRate"), (double) a.get("completionRate")));

        BigDecimal overallCompletion = allEnrolled > 0
                ? BigDecimal.valueOf(allCompleted).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(allEnrolled), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal overallPass = allGraded > 0
                ? BigDecimal.valueOf(allPassed).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(allGraded), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("courses", courseList);
        result.put("totalStudents", allEnrolled);
        result.put("totalGraded", allGraded);
        result.put("overallCompletionRate", overallCompletion.doubleValue());
        result.put("overallPassRate", overallPass.doubleValue());

        return result;
    }

    // ==================== 操作日志 ====================

    @Override
    public IPage<Map<String, Object>> getOperationLogs(String module, String keyword,
                                                        Integer page, Integer pageSize) {
        Page<com.iotp.entity.SysOperationLog> logPage = new Page<>(page, pageSize);
        QueryWrapper<com.iotp.entity.SysOperationLog> qw = new QueryWrapper<>();
        if (module != null && !module.isEmpty()) {
            qw.eq("module", module);
        }
        if (keyword != null && !keyword.isEmpty()) {
            qw.and(w -> w.like("description", keyword)
                    .or().like("operator_name", keyword));
        }
        qw.orderByDesc("create_time");

        IPage<com.iotp.entity.SysOperationLog> pageResult = sysOperationLogMapper.selectPage(logPage, qw);

        List<Map<String, Object>> list = new ArrayList<>();
        for (com.iotp.entity.SysOperationLog ol : pageResult.getRecords()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", ol.getId());
            item.put("module", ol.getModule());
            item.put("operation", ol.getOperation());
            item.put("description", ol.getDescription());
            item.put("operatorName", ol.getOperatorName());
            item.put("requestMethod", ol.getRequestMethod());
            item.put("requestUrl", ol.getRequestUrl());
            item.put("resultStatus", ol.getResultStatus());
            item.put("errorMsg", ol.getErrorMsg());
            item.put("durationMs", ol.getDurationMs());
            item.put("ip", ol.getIp());
            item.put("createTime", ol.getCreateTime());
            list.add(item);
        }

        Page<Map<String, Object>> resultPage = new Page<>(logPage.getCurrent(), logPage.getSize());
        resultPage.setTotal(pageResult.getTotal());
        resultPage.setRecords(list);
        return resultPage;
    }

    // ==================== 系统监控 ====================

    @Override
    public Map<String, Object> getSystemMonitor() {
        Map<String, Object> result = new LinkedHashMap<>();

        // 1. 系统指标（JVM 内存 + 磁盘）
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        long usedMemory = totalMemory - freeMemory;

        Map<String, Object> systemMetrics = new LinkedHashMap<>();
        systemMetrics.put("memoryUsed", usedMemory / (1024 * 1024));       // MB
        systemMetrics.put("memoryTotal", totalMemory / (1024 * 1024));
        systemMetrics.put("memoryMax", maxMemory / (1024 * 1024));
        systemMetrics.put("memoryUsagePercent", totalMemory > 0
                ? BigDecimal.valueOf(usedMemory).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalMemory), 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        systemMetrics.put("cpuCores", runtime.availableProcessors());

        // 磁盘空间（应用所在目录）
        java.io.File appDir = new java.io.File(".");
        long diskTotal = appDir.getTotalSpace();
        long diskFree = appDir.getFreeSpace();
        long diskUsed = diskTotal - diskFree;
        systemMetrics.put("diskUsed", diskUsed / (1024 * 1024 * 1024));   // GB
        systemMetrics.put("diskTotal", diskTotal / (1024 * 1024 * 1024));
        systemMetrics.put("diskUsagePercent", diskTotal > 0
                ? BigDecimal.valueOf(diskUsed).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(diskTotal), 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);

        result.put("systemMetrics", systemMetrics);

        // 2. 用户统计
        Long studentRoleId = getRoleIdByCode("STUDENT");
        Long teacherRoleId = getRoleIdByCode("TEACHER");
        Long adminRoleId = getRoleIdByCode("ADMIN");

        QueryWrapper<SysUser> activeUserQw = new QueryWrapper<>();
        activeUserQw.eq("status", 1).eq("is_deleted", 0);
        long activeUsers = sysUserMapper.selectCount(activeUserQw);

        QueryWrapper<SysUser> allUserQw = new QueryWrapper<>();
        allUserQw.eq("is_deleted", 0);
        long totalUsers = sysUserMapper.selectCount(allUserQw);

        // 近24小时登录的用户数（近似在线）
        QueryWrapper<SysUser> recentLoginQw = new QueryWrapper<>();
        recentLoginQw.ge("last_login_time", LocalDateTime.now().minusHours(24))
                .eq("is_deleted", 0);
        long todayActiveUsers = sysUserMapper.selectCount(recentLoginQw);

        Map<String, Object> userStats = new LinkedHashMap<>();
        userStats.put("totalUsers", totalUsers);
        userStats.put("activeUsers", activeUsers);
        userStats.put("todayActive", todayActiveUsers);
        userStats.put("students", studentRoleId != null
                ? sysUserMapper.selectCount(new QueryWrapper<SysUser>().eq("role_id", studentRoleId).eq("is_deleted", 0))
                : 0);
        userStats.put("teachers", teacherRoleId != null
                ? sysUserMapper.selectCount(new QueryWrapper<SysUser>().eq("role_id", teacherRoleId).eq("is_deleted", 0))
                : 0);
        userStats.put("admins", adminRoleId != null
                ? sysUserMapper.selectCount(new QueryWrapper<SysUser>().eq("role_id", adminRoleId).eq("is_deleted", 0))
                : 0);
        result.put("userStats", userStats);

        // 3. 审核汇总
        QueryWrapper<CoursePlan> pendingCourseQw = new QueryWrapper<>();
        pendingCourseQw.eq("audit_status", AUDIT_PENDING);
        long pendingCourses = coursePlanMapper.selectCount(pendingCourseQw);

        QueryWrapper<ExperimentTask> pendingTaskQw = new QueryWrapper<>();
        pendingTaskQw.eq("audit_status", AUDIT_PENDING);
        long pendingTasks = experimentTaskMapper.selectCount(pendingTaskQw);

        QueryWrapper<TeachingResource> pendingResQw = new QueryWrapper<>();
        pendingResQw.eq("audit_status", AUDIT_PENDING);
        long pendingResources = teachingResourceMapper.selectCount(pendingResQw);

        long totalApproved = coursePlanMapper.selectCount(
                new QueryWrapper<CoursePlan>().eq("audit_status", AUDIT_APPROVED))
                + experimentTaskMapper.selectCount(
                new QueryWrapper<ExperimentTask>().eq("audit_status", AUDIT_APPROVED))
                + teachingResourceMapper.selectCount(
                new QueryWrapper<TeachingResource>().eq("audit_status", AUDIT_APPROVED));

        long totalRejected = coursePlanMapper.selectCount(
                new QueryWrapper<CoursePlan>().eq("audit_status", AUDIT_REJECTED))
                + experimentTaskMapper.selectCount(
                new QueryWrapper<ExperimentTask>().eq("audit_status", AUDIT_REJECTED))
                + teachingResourceMapper.selectCount(
                new QueryWrapper<TeachingResource>().eq("audit_status", AUDIT_REJECTED));

        // 今日审核统计
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.atTime(23, 59, 59);

        QueryWrapper<SysAuditLog> todayApprovedQw = new QueryWrapper<>();
        todayApprovedQw.eq("action", AUDIT_APPROVED)
                .between("create_time", todayStart, todayEnd);
        long todayApproved = sysAuditLogMapper.selectCount(todayApprovedQw);

        QueryWrapper<SysAuditLog> todayRejectedQw = new QueryWrapper<>();
        todayRejectedQw.eq("action", AUDIT_REJECTED)
                .between("create_time", todayStart, todayEnd);
        long todayRejected = sysAuditLogMapper.selectCount(todayRejectedQw);

        Map<String, Object> auditSummary = new LinkedHashMap<>();
        auditSummary.put("pendingCourses", pendingCourses);
        auditSummary.put("pendingTasks", pendingTasks);
        auditSummary.put("pendingResources", pendingResources);
        auditSummary.put("totalPending", pendingCourses + pendingTasks + pendingResources);
        auditSummary.put("totalApproved", totalApproved);
        auditSummary.put("totalRejected", totalRejected);
        auditSummary.put("todayApproved", todayApproved);
        auditSummary.put("todayRejected", todayRejected);
        result.put("auditSummary", auditSummary);

        // 4. 最近操作日志（最近 30 条，来自 sys_operation_log）
        Page<com.iotp.entity.SysOperationLog> logPage = new Page<>(1, 30);
        QueryWrapper<com.iotp.entity.SysOperationLog> logQw = new QueryWrapper<>();
        logQw.orderByDesc("create_time");
        IPage<com.iotp.entity.SysOperationLog> logPageResult = sysOperationLogMapper.selectPage(logPage, logQw);

        List<Map<String, Object>> logList = new ArrayList<>();
        for (com.iotp.entity.SysOperationLog ol : logPageResult.getRecords()) {
            String level = (ol.getResultStatus() != null && ol.getResultStatus() == 0) ? "WARN" : "INFO";

            Map<String, Object> logItem = new LinkedHashMap<>();
            logItem.put("time", ol.getCreateTime());
            logItem.put("user", ol.getOperatorName());
            logItem.put("action", ol.getDescription() != null ? ol.getDescription()
                    : ol.getModule() + " - " + ol.getOperation());
            logItem.put("type", ol.getModule());
            logItem.put("level", level);
            logList.add(logItem);
        }
        result.put("recentLogs", logList);

        // 5. 近24小时活跃用户列表（最近登录的10个用户）
        QueryWrapper<SysUser> activeListQw = new QueryWrapper<>();
        activeListQw.isNotNull("last_login_time")
                .eq("is_deleted", 0)
                .orderByDesc("last_login_time")
                .last("LIMIT 10");
        activeListQw.eq("is_deleted", 0);
        List<SysUser> recentUsers = sysUserMapper.selectList(
                new QueryWrapper<SysUser>()
                        .isNotNull("last_login_time")
                        .eq("is_deleted", 0)
                        .orderByDesc("last_login_time")
                        .last("LIMIT 10"));

        List<Map<String, Object>> onlineList = new ArrayList<>();
        for (SysUser u : recentUsers) {
            Map<String, Object> userItem = new LinkedHashMap<>();
            userItem.put("account", u.getUsername());
            userItem.put("userName", u.getRealName());
            userItem.put("roleId", u.getRoleId());
            String[] roleInfo = getRoleCodeAndName(u.getRoleId());
            userItem.put("role", roleInfo[0]);
            userItem.put("roleName", roleInfo[1]);
            userItem.put("lastLoginTime", u.getLastLoginTime());
            // 在线时长（近似：从最后登录时间到现在）
            if (u.getLastLoginTime() != null) {
                long minutes = java.time.Duration.between(u.getLastLoginTime(), LocalDateTime.now()).toMinutes();
                userItem.put("duration", Math.max(0, minutes));
            } else {
                userItem.put("duration", 0);
            }
            onlineList.add(userItem);
        }
        result.put("onlineUsers", onlineList);

        return result;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 构建完整的用户信息 Map（含角色、部门、班级名称）
     */
    private Map<String, Object> buildUserMap(SysUser user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.getId());
        map.put("userId", user.getId());          // 前端编辑/操作统一用
        map.put("username", user.getUsername());
        map.put("account", user.getUsername());
        map.put("realName", user.getRealName());
        map.put("userName", user.getRealName());
        map.put("email", user.getEmail());
        map.put("phone", user.getPhone());
        map.put("avatar", user.getAvatar());
        map.put("roleId", user.getRoleId());

        // 同时填充 roleCode 和 roleName，避免前端因缺少字段误判
        String[] roleCodeAndName = getRoleCodeAndName(user.getRoleId());
        map.put("role", roleCodeAndName[0]);
        map.put("roleName", roleCodeAndName[1]);
        map.put("departmentId", user.getDepartmentId());
        map.put("departmentName", getDepartmentName(user.getDepartmentId()));
        map.put("department", getDepartmentName(user.getDepartmentId()));
        map.put("classId", user.getClassId());
        map.put("className", getClassName(user.getClassId()));

        // 状态字段：同时返回整数值供后端使用 + 字符串供前端显示
        Integer statusInt = user.getStatus();
        map.put("status", statusInt != null ? statusInt : 1);
        map.put("statusType", statusToCode(statusInt));

        map.put("loginFailCount", user.getLoginFailCount());
        map.put("lastLoginTime", user.getLastLoginTime());
        map.put("createTime", user.getCreateTime());
        map.put("updateTime", user.getUpdateTime());
        return map;
    }

    /**
     * 状态整数值 → 状态编码字符串
     */
    private String statusToCode(Integer status) {
        if (status == null || status == 1) return "ACTIVE";
        if (status == 0) return "FROZEN";
        return "LOCKED";
    }

    /**
     * 解析 Excel 工作表为行数据列表
     */
    private List<Map<String, String>> parseExcelSheet(Sheet sheet) {
        List<Map<String, String>> rows = new ArrayList<>();
        if (sheet.getPhysicalNumberOfRows() <= 1) {
            return rows; // 只有表头或空
        }

        // 读取表头
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            return rows;
        }

        // 扫描所有行取最大列数（表头 + 数据行），避免 getLastCellNum() 漏掉末尾列
        int cellCount = headerRow.getLastCellNum();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row dataRow = sheet.getRow(i);
            if (dataRow != null) {
                int c = dataRow.getLastCellNum();
                if (c > cellCount) cellCount = c;
            }
        }

        // 用扫描出的最大列数读取表头（空白单元格标题为 ""）
        List<String> headers = new ArrayList<>();
        for (int i = 0; i < cellCount; i++) {
            Cell cell = headerRow.getCell(i);
            headers.add(cell != null ? getCellStringValue(cell) : "");
        }

        // 读取数据行（如果表头为空，自动生成列号作为 key，防止数据丢失）
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            Map<String, String> rowData = new LinkedHashMap<>();
            for (int j = 0; j < cellCount; j++) {
                String key = headers.get(j);
                // 表头为空时用列号兜底
                if (key.isEmpty()) key = "col" + (j + 1);
                Cell cell = row.getCell(j);
                rowData.put(key, cell != null ? getCellStringValue(cell) : "");
            }
            rows.add(rowData);
        }

        return rows;
    }

    /**
     * 获取 Excel 单元格的字符串值
     */
    private String getCellStringValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                double val = cell.getNumericCellValue();
                if (val == Math.floor(val) && !Double.isInfinite(val)) {
                    return String.valueOf((long) val);
                }
                return String.valueOf(val);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return String.valueOf(cell.getNumericCellValue());
                } catch (Exception e) {
                    return cell.getStringCellValue();
                }
            default:
                return "";
        }
    }

    /**
     * 获取角色编码和名称
     * @return [roleCode, roleName]
     */
    private String[] getRoleCodeAndName(Long roleId) {
        if (roleId == null) return new String[]{"", ""};
        SysRole role = sysRoleMapper.selectById(roleId);
        if (role != null) {
            return new String[]{role.getRoleCode() != null ? role.getRoleCode() : "", role.getRoleName()};
        }
        return new String[]{"", ""};
    }

    /**
     * 根据角色编码查找角色ID
     */
    private Long getRoleIdByCode(String roleCode) {
        QueryWrapper<SysRole> qw = new QueryWrapper<>();
        qw.eq("role_code", roleCode);
        SysRole role = sysRoleMapper.selectOne(qw);
        return role != null ? role.getId() : null;
    }

    /**
     * 获取部门名称
     */
    private String getDepartmentName(Long departmentId) {
        if (departmentId == null) return "";
        SysDepartment dept = sysDepartmentMapper.selectById(departmentId);
        return dept != null ? dept.getDeptName() : "";
    }

    /**
     * 根据部门名称查找部门ID
     */
    @Override
    public Long getDepartmentIdByName(String deptName) {
        if (deptName == null || deptName.trim().isEmpty()) return null;
        QueryWrapper<SysDepartment> qw = new QueryWrapper<>();
        qw.eq("dept_name", deptName.trim());
        SysDepartment dept = sysDepartmentMapper.selectOne(qw);
        return dept != null ? dept.getId() : null;
    }

    /**
     * 获取班级名称
     */
    private String getClassName(Long classId) {
        if (classId == null) return "";
        SysClass cls = sysClassMapper.selectById(classId);
        return cls != null ? cls.getClassName() : "";
    }

    /**
     * 获取用户真实姓名
     */
    private String getUserRealName(Long userId) {
        if (userId == null) return "";
        SysUser user = sysUserMapper.selectById(userId);
        return user != null ? user.getRealName() : "";
    }

    /**
     * 获取学期名称
     */
    private String getSemesterName(Long semesterId) {
        if (semesterId == null) return "";
        SysSemester sem = sysSemesterMapper.selectById(semesterId);
        return sem != null ? sem.getSemesterName() : "";
    }

    /**
     * 获取课程名称
     */
    private String getCourseName(Long courseId) {
        if (courseId == null) return "";
        Course course = courseMapper.selectById(courseId);
        return course != null ? course.getCourseName() : "";
    }

    /**
     * 获取实验项目名称
     */
    private String getExperimentProjectName(Long projectId) {
        if (projectId == null) return "";
        ExperimentProject project = experimentProjectMapper.selectById(projectId);
        return project != null ? project.getProjectName() : "";
    }

    /**
     * 获取资源分类名称
     */
    private String getResourceCategoryName(Long categoryId) {
        if (categoryId == null) return "";
        ResourceCategory category = resourceCategoryMapper.selectById(categoryId);
        return category != null ? category.getCategoryName() : "";
    }

    /**
     * 安全解析字符串为 int，失败返回默认值
     */
    private int parseIntOrDefault(String value, int defaultValue) {
        if (value == null || value.trim().isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** 从 sys_config 表读取整数配置 */
    private int getConfigInt(String key, int defaultValue) {
        try {
            QueryWrapper<SysConfig> qw = new QueryWrapper<>();
            qw.eq("config_key", key);
            SysConfig cfg = sysConfigMapper.selectOne(qw);
            if (cfg != null && cfg.getConfigValue() != null) {
                return Integer.parseInt(cfg.getConfigValue().trim());
            }
        } catch (Exception e) { /* ignore */ }
        return defaultValue;
    }
}
