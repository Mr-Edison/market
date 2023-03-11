package com.fuint.common.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fuint.common.dto.AccountInfo;
import com.fuint.common.enums.GenderEnum;
import com.fuint.common.enums.MemberSourceEnum;
import com.fuint.common.enums.StatusEnum;
import com.fuint.common.enums.UserActionEnum;
import com.fuint.common.service.*;
import com.fuint.common.util.CommonUtil;
import com.fuint.common.util.SeqUtil;
import com.fuint.common.util.TimeUtils;
import com.fuint.common.util.TokenUtil;
import com.fuint.framework.annoation.OperationServiceLog;
import com.fuint.framework.exception.BusinessCheckException;
import com.fuint.framework.pagination.PaginationRequest;
import com.fuint.framework.pagination.PaginationResponse;
import com.fuint.repository.mapper.MtUserActionMapper;
import com.fuint.repository.mapper.MtUserGradeMapper;
import com.fuint.repository.mapper.MtUserMapper;
import com.fuint.repository.model.*;
import com.fuint.utils.StringUtil;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import org.apache.commons.lang.StringUtils;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.*;

/**
 * 会员业务接口实现类
 *
 * Created by FSQ
 * CopyRight https://www.fuint.cn
 */
@Service
public class MemberServiceImpl extends ServiceImpl<MtUserMapper, MtUser> implements MemberService {

    @Resource
    private MtUserMapper mtUserMapper;

    @Resource
    private MtUserGradeMapper mtUserGradeMapper;

    @Resource
    private MtUserActionMapper mtUserActionMapper;

    /**
     * 短信发送接口
     */
    @Resource
    private SendSmsService sendSmsService;

    /**
     * 会员等级接口
     * */
    @Resource
    private UserGradeService userGradeService;

    /**
     * 会员等级接口
     * */
    @Resource
    private OpenGiftService openGiftService;

    /**
     * 后台账户服务接口
     */
    @Resource
    private AccountService accountService;

    /**
     * 员工接口
     */
    @Resource
    private StaffService staffService;

    /**
     * 会员行为接口
     */
    @Resource
    private UserActionService userActionService;

    /**
     * 更新活跃时间
     * @param userId 会员ID
     * @return
     * */
    @Override
    @Transactional
    public boolean updateActiveTime(Integer userId) throws BusinessCheckException {
        MtUser mtUser = this.queryMemberById(userId);
        if (mtUser != null) {
            if (!mtUser.getStatus().equals(StatusEnum.ENABLED.getKey())) {
                return false;
            }

            Date lastUpdateTime = mtUser.getUpdateTime();
            Date registerTime = mtUser.getCreateTime();
            if (lastUpdateTime != null) {
                Long timestampLast = Long.valueOf(TimeUtils.date2timeStamp(lastUpdateTime));
                Long timestampNow = System.currentTimeMillis() / 1000;
                Long minute = timestampNow - timestampLast;

                // 5分钟更新一次
                if (minute >= 300 || registerTime.equals(lastUpdateTime)) {
                    synchronized(MemberServiceImpl.class) {
                        Date activeTime = new Date();
                        mtUserMapper.updateActiveTime(mtUser.getId(), activeTime);
                        // 记录会员行为
                        MtUserAction mtUserAction = new MtUserAction();
                        mtUserAction.setUserId(mtUser.getId());
                        mtUserAction.setStoreId(mtUser.getStoreId());
                        mtUserAction.setParam(TimeUtils.formatDate(activeTime, "yyyy-MM-dd HH:mm:ss"));
                        mtUserAction.setAction(UserActionEnum.LOGIN.getKey());
                        mtUserAction.setDescription(UserActionEnum.LOGIN.getValue());
                        userActionService.addUserAction(mtUserAction);
                    }
                }
            }
        }

        return true;
    }

    /**
     * 获取当前操作会员信息
     * @param userId
     * @param token
     * @return
     * */
    @Override
    public MtUser getCurrentUserInfo(HttpServletRequest request, Integer userId, String token) throws BusinessCheckException {
        MtUser mtUser = null;

        // 没有会员信息，则查询是否是后台收银员下单
        AccountInfo accountInfo = TokenUtil.getAccountInfoByToken(token);
        if (accountInfo != null) {
            // 输入了会员ID就用会员的账号下单，否则用员工账号下单
            if (userId > 0) {
                mtUser = this.queryMemberById(userId);
            } else {
                Integer accountId = accountInfo.getId();
                TAccount account = accountService.getAccountInfoById(accountId);
                if (account != null) {
                    if (account.getStaffId() > 0) {
                        MtStaff staff = staffService.queryStaffById(account.getStaffId());
                        if (staff != null) {
                            mtUser = this.queryMemberById(staff.getUserId());
                            if (mtUser != null && (mtUser.getStoreId() == null || mtUser.getStoreId() <= 0)) {
                                mtUser.setStoreId(staff.getStoreId());
                                this.updateById(mtUser);
                            }
                        }
                    }
                }
            }
        }

        return mtUser;
    }

    /**
     * 分页查询会员列表
     *
     * @param paginationRequest
     * @return
     */
    @Override
    public PaginationResponse<MtUser> queryMemberListByPagination(PaginationRequest paginationRequest) {
        Page<MtUser> pageHelper = PageHelper.startPage(paginationRequest.getCurrentPage(), paginationRequest.getPageSize());
        LambdaQueryWrapper<MtUser> lambdaQueryWrapper = Wrappers.lambdaQuery();
        lambdaQueryWrapper.ne(MtUser::getStatus, StatusEnum.DISABLE.getKey());

        String name = paginationRequest.getSearchParams().get("name") == null ? "" : paginationRequest.getSearchParams().get("name").toString();
        if (StringUtils.isNotBlank(name)) {
            lambdaQueryWrapper.like(MtUser::getName, name);
        }
        String id = paginationRequest.getSearchParams().get("id") == null ? "" : paginationRequest.getSearchParams().get("id").toString();
        if (StringUtils.isNotBlank(id)) {
            lambdaQueryWrapper.eq(MtUser::getId, id);
        }
        String mobile = paginationRequest.getSearchParams().get("mobile") == null ? "" : paginationRequest.getSearchParams().get("mobile").toString();
        if (StringUtils.isNotBlank(mobile)) {
            lambdaQueryWrapper.like(MtUser::getMobile, mobile);
        }
        String birthday = paginationRequest.getSearchParams().get("birthday") == null ? "" : paginationRequest.getSearchParams().get("birthday").toString();
        if (StringUtils.isNotBlank(birthday)) {
            lambdaQueryWrapper.like(MtUser::getBirthday, birthday);
        }
        String userNo = paginationRequest.getSearchParams().get("userNo") == null ? "" : paginationRequest.getSearchParams().get("userNo").toString();
        if (StringUtils.isNotBlank(userNo)) {
            lambdaQueryWrapper.eq(MtUser::getUserNo, userNo);
        }
        String gradeId = paginationRequest.getSearchParams().get("gradeId") == null ? "" : paginationRequest.getSearchParams().get("gradeId").toString();
        if (StringUtils.isNotBlank(gradeId)) {
            lambdaQueryWrapper.eq(MtUser::getGradeId, gradeId);
        }
        String storeId = paginationRequest.getSearchParams().get("storeId") == null ? "" : paginationRequest.getSearchParams().get("storeId").toString();
        if (StringUtils.isNotBlank(storeId)) {
            lambdaQueryWrapper.eq(MtUser::getStoreId, storeId);
        }
        String status = paginationRequest.getSearchParams().get("status") == null ? "" : paginationRequest.getSearchParams().get("status").toString();
        if (StringUtils.isNotBlank(status)) {
            lambdaQueryWrapper.eq(MtUser::getStatus, status);
        }

        lambdaQueryWrapper.orderByDesc(MtUser::getUpdateTime);
        List<MtUser> userList = mtUserMapper.selectList(lambdaQueryWrapper);
        List<MtUser> dataList = new ArrayList<>();
        for (MtUser user : userList) {
            String phone = user.getMobile();
            // 隐藏手机号中间四位
            if (phone != null && StringUtil.isNotEmpty(phone) && phone.length() == 11) {
                user.setMobile(phone.substring(0, 3) + "****" + phone.substring(7));
            }
            dataList.add(user);
        }

        PageRequest pageRequest = PageRequest.of(paginationRequest.getCurrentPage(), paginationRequest.getPageSize());
        PageImpl pageImpl = new PageImpl(dataList, pageRequest, pageHelper.getTotal());
        PaginationResponse<MtUser> paginationResponse = new PaginationResponse(pageImpl, MtUser.class);
        paginationResponse.setTotalPages(pageHelper.getPages());
        paginationResponse.setTotalElements(pageHelper.getTotal());
        paginationResponse.setContent(dataList);

        return paginationResponse;
    }

    /**
     * 添加会员
     *
     * @param  mtUser
     * @throws BusinessCheckException
     */
    @Override
    @OperationServiceLog(description = "新增会员信息")
    public MtUser addMember(MtUser mtUser) throws BusinessCheckException {
        // 手机号已存在
        if (StringUtil.isNotEmpty(mtUser.getMobile())) {
            MtUser userInfo = this.queryMemberByMobile(mtUser.getMobile());
            if (userInfo != null) {
                return userInfo;
            }
        }

        String userNo = CommonUtil.createUserNo();
        // 会员名称已存在
        List<MtUser> userList = mtUserMapper.queryMemberByName(mtUser.getName());
        if (userList.size() > 0) {
            mtUser.setName(userNo);
        }

        if (StringUtil.isEmpty(mtUser.getGradeId())) {
            MtUserGrade grade = userGradeService.getInitUserGrade();
            mtUser.setGradeId(grade.getId()+"");
        }

        mtUser.setUserNo(userNo);
        mtUser.setBalance(new BigDecimal(0));
        if (mtUser.getPoint() == null || mtUser.getPoint() < 1) {
            mtUser.setPoint(0);
        }
        if (StringUtil.isEmpty(mtUser.getIdcard())) {
            mtUser.setIdcard("");
        }
        mtUser.setSex(mtUser.getSex());
        mtUser.setStatus(StatusEnum.ENABLED.getKey());
        Date time = new Date();
        mtUser.setCreateTime(time);
        mtUser.setUpdateTime(time);
        if (mtUser.getStoreId() != null) {
            mtUser.setStoreId(mtUser.getStoreId());
        } else {
            mtUser.setStoreId(0);
        }

        // 密码加密
        if (mtUser.getPassword() != null && StringUtil.isNotEmpty(mtUser.getPassword())) {
            String salt = SeqUtil.getRandomLetter(4);
            String password = CommonUtil.createPassword(mtUser.getPassword(), salt);
            mtUser.setPassword(password);
            mtUser.setSalt(salt);
            mtUser.setSource(MemberSourceEnum.REGISTER_BY_ACCOUNT.getKey());
        }

        if (mtUser.getSource() == null || StringUtil.isEmpty(mtUser.getSource())) {
            mtUser.setSource(MemberSourceEnum.BACKEND_ADD.getKey());
        }

        boolean result = this.save(mtUser);
        if (!result) {
           return null;
        }

        mtUser = this.queryMemberById(mtUser.getId());

        // 开卡赠礼
        openGiftService.openGift(mtUser.getId(), Integer.parseInt(mtUser.getGradeId()));

        // 新增用户发短信通知
        if (mtUser.getId() > 0 && mtUser.getStatus().equals(StatusEnum.ENABLED.getKey())) {
            // 发送短信
            List<String> mobileList = new ArrayList<String>();
            mobileList.add(mtUser.getMobile());
            // 短信模板
            try {
                Map<String, String> params = new HashMap<>();
                sendSmsService.sendSms("register-sms", mobileList, params);
            } catch (Exception e) {
                // empty
            }
        }

        return mtUser;
    }

    /**
     * 更新会员信息
     *
     * @param mtUser
     * @throws BusinessCheckException
     */
    @Override
    @Transactional
    @OperationServiceLog(description = "修改会员信息")
    public MtUser updateMember(MtUser mtUser) throws BusinessCheckException {
        mtUser.setUpdateTime(new Date());

        // 检查会员号是否重复
        if (StringUtil.isNotEmpty(mtUser.getUserNo())) {
            List<MtUser> userList = mtUserMapper.findMembersByUserNo(mtUser.getUserNo());
            if (userList.size() > 0) {
                for(MtUser user: userList) {
                    MtUser userInfo = user;
                    if (userInfo.getId().intValue() != mtUser.getId().intValue()) {
                        throw new BusinessCheckException("该会员号与会员ID等于" + userInfo.getId() + "重复啦");
                    }
                }
            }
        }

        this.updateById(mtUser);
        return mtUser;
    }

    /**
     * 通过手机号新增会员
     *
     * @param mobile
     * @throws BusinessCheckException
     */
    @Override
    @Transactional
    @OperationServiceLog(description = "通过手机号新增会员")
    public MtUser addMemberByMobile(String mobile) throws BusinessCheckException {
        MtUser mtUser = new MtUser();
        mtUser.setUserNo(CommonUtil.createUserNo());
        String nickName = mobile.replaceAll("(\\d{3})\\d{4}(\\d{4})","$1****$2");
        mtUser.setName(nickName);
        mtUser.setMobile(mobile);
        MtUserGrade grade = userGradeService.getInitUserGrade();
        mtUser.setGradeId(grade.getId()+"");
        Date time = new Date();
        mtUser.setCreateTime(time);
        mtUser.setUpdateTime(time);
        mtUser.setBalance(new BigDecimal(0));
        mtUser.setPoint(0);
        mtUser.setDescription("手机号登录自动注册");
        mtUser.setIdcard("");
        mtUser.setStatus(StatusEnum.ENABLED.getKey());
        mtUser.setStoreId(0);
        mtUser.setSource(MemberSourceEnum.MOBILE_LOGIN.getKey());

        mtUserMapper.insert(mtUser);

        mtUser = this.queryMemberByMobile(mobile);

        // 开卡赠礼
        openGiftService.openGift(mtUser.getId(), Integer.parseInt(mtUser.getGradeId()));

        return mtUser;
    }

    /**
     * 根据手机号获取会员信息
     *
     * @param mobile 手机号
     * @throws BusinessCheckException
     */
    @Override
    public MtUser queryMemberByMobile(String mobile) {
        if (mobile == null || StringUtil.isEmpty(mobile)) {
            return null;
        }
        List<MtUser> mtUser = mtUserMapper.queryMemberByMobile(mobile);
        if (mtUser.size() > 0) {
            return mtUser.get(0);
        } else {
            return null;
        }
    }

    /**
     * 根据会员号号获取会员信息
     *
     * @param  userNo 会员号
     * @throws BusinessCheckException
     */
    @Override
    public MtUser queryMemberByUserNo(String userNo) {
        if (userNo == null || StringUtil.isEmpty(userNo)) {
            return null;
        }
        List<MtUser> mtUser = mtUserMapper.findMembersByUserNo(userNo);
        if (mtUser.size() > 0) {
            return mtUser.get(0);
        } else {
            return null;
        }
    }

    /**
     * 根据会员ID获取会员信息
     *
     * @param id 会员ID
     * @return
     * @throws BusinessCheckException
     */
    @Override
    public MtUser queryMemberById(Integer id) throws BusinessCheckException {
        MtUser mtUser = mtUserMapper.selectById(id);

        if (mtUser != null) {
            // 检查会员是否过期，过期就把会员等级置为初始等级
            Date endTime = mtUser.getEndTime();
            if (endTime != null) {
                Date now = new Date();
                if (endTime.before(now)) {
                    MtUserGrade grade = userGradeService.getInitUserGrade();
                    if (!mtUser.getGradeId().equals(grade.getId())) {
                        mtUser.setGradeId(grade.getId().toString());
                        this.updateById(mtUser);
                    }
                }
            }
        }

        return mtUser;
    }

    /**
     * 根据会员名称获取会员信息
     *
     * @param name 会员名称
     * @throws BusinessCheckException
     */
    @Override
    public MtUser queryMemberByName(String name) {
        if (StringUtil.isNotEmpty(name)) {
            List<MtUser> userList = mtUserMapper.queryMemberByName(name);
            if (userList.size() == 1) {
                return userList.get(0);
            }
        }
        return null;
    }

    /**
     * 根据openId获取会员信息(为空就注册)
     *
     * @param  openId
     * @throws BusinessCheckException
     */
    @Override
    public MtUser queryMemberByOpenId(String openId, JSONObject userInfo) throws BusinessCheckException {
        MtUser user = mtUserMapper.queryMemberByOpenId(openId);

        String avatar = StringUtil.isNotEmpty(userInfo.getString("avatarUrl")) ? userInfo.getString("avatarUrl") : "";
        String gender = StringUtil.isNotEmpty(userInfo.getString("gender")) ? userInfo.getString("gender") : GenderEnum.MAN.getKey().toString();
        String country = StringUtil.isNotEmpty(userInfo.getString("country")) ? userInfo.getString("country") : "";
        String province = StringUtil.isNotEmpty(userInfo.getString("province")) ? userInfo.getString("province") : "";
        String city = StringUtil.isNotEmpty(userInfo.getString("city")) ? userInfo.getString("city") : "";
        String storeId = StringUtil.isNotEmpty(userInfo.getString("storeId")) ? userInfo.getString("storeId") : "0";

        if (user == null) {
            String nickName = userInfo.getString("nickName");
            String mobile = StringUtil.isNotEmpty(userInfo.getString("phone")) ? userInfo.getString("phone") : "";

            MtUser mtUser = new MtUser();
            if (StringUtil.isNotEmpty(mobile)) {
                MtUser mtUserMobile = this.queryMemberByMobile(mobile);
                if (mtUserMobile != null) {
                    mtUser = mtUserMobile;
                }
            }

            // 昵称为空，用手机号
            if (StringUtil.isEmpty(nickName) && StringUtil.isNotEmpty(mobile)) {
                nickName = mobile.replaceAll("(\\d{3})\\d{4}(\\d{4})","$1****$2");
            }
            String userNo = CommonUtil.createUserNo();
            mobile = CommonUtil.replaceXSS(mobile);
            avatar = CommonUtil.replaceXSS(avatar);
            nickName = CommonUtil.replaceXSS(nickName);
            mtUser.setUserNo(userNo);
            mtUser.setMobile(mobile);
            mtUser.setAvatar(avatar);
            mtUser.setName(nickName);
            mtUser.setOpenId(openId);
            MtUserGrade grade = userGradeService.getInitUserGrade();
            mtUser.setGradeId(grade.getId()+"");
            Date time = new Date();
            mtUser.setCreateTime(time);
            mtUser.setUpdateTime(time);
            mtUser.setBalance(new BigDecimal(0));
            mtUser.setPoint(0);
            mtUser.setDescription("微信登录自动注册");
            mtUser.setIdcard("");
            mtUser.setStatus(StatusEnum.ENABLED.getKey());
            mtUser.setAddress(country + province + city);
            mtUser.setSex(Integer.parseInt(gender));
            if (StringUtil.isNotEmpty(storeId)) {
                mtUser.setStoreId(Integer.parseInt(storeId));
            } else {
                mtUser.setStoreId(0);
            }
            mtUser.setSource(MemberSourceEnum.WECHAT_LOGIN.getKey());
            if (mtUser.getId() == null || mtUser.getId() <= 0) {
                this.save(mtUser);
            } else {
                this.updateById(mtUser);
            }
            user = mtUserMapper.queryMemberByOpenId(openId);

            // 开卡赠礼
            openGiftService.openGift(user.getId(), Integer.parseInt(user.getGradeId()));
        } else {
            // 已被禁用
            if (user.getStatus().equals(StatusEnum.DISABLE.getKey())) {
               return null;
            }
            // 补充会员号
            if (StringUtil.isEmpty(user.getUserNo())) {
                user.setUserNo(CommonUtil.createUserNo());
                this.updateById(user);
            }
        }

        return user;
    }

    /**
     * 根据等级ID获取会员等级信息
     *
     * @param id 等级ID
     * @throws BusinessCheckException
     */
    @Override
    public MtUserGrade queryMemberGradeByGradeId(Integer id) {
        MtUserGrade gradeInfo = mtUserGradeMapper.selectById(id);
        return gradeInfo;
    }

    /**
     * 禁用会员
     *
     * @param id 会员ID
     * @param operator 操作人
     * @throws BusinessCheckException
     */
    @Override
    @OperationServiceLog(description = "删除会员信息")
    public Integer deleteMember(Integer id, String operator) {
        MtUser mtUser = mtUserMapper.selectById(id);
        if (null == mtUser) {
            return 0;
        }

        mtUser.setStatus(StatusEnum.DISABLE.getKey());
        mtUser.setUpdateTime(new Date());
        mtUser.setOperator(operator);

        this.updateById(mtUser);

        return mtUser.getId();
    }

    /**
     * 根据条件搜索会员分组
     * */
    @Override
    public List<MtUserGrade> queryMemberGradeByParams(Map<String, Object> params) {
        if (params == null) {
            params = new HashMap<>();
        }
        List<MtUserGrade> result = mtUserGradeMapper.selectByMap(params);
        return result;
    }

    /**
     * 获取会员数量
     * */
    @Override
    public Long getUserCount(Integer storeId) {
        if (storeId > 0) {
            return mtUserMapper.getStoreUserCount(storeId);
        } else {
            return mtUserMapper.getUserCount();
        }
    }

    /**
     * 获取会员数量
     * */
    @Override
    public Long getUserCount(Integer storeId, Date beginTime, Date endTime) {
        if (storeId > 0) {
            return mtUserMapper.getStoreUserCountByTime(storeId, beginTime, endTime);
        } else {
            return mtUserMapper.getUserCountByTime(beginTime, endTime);
        }
    }

    /**
     * 获取会员数量
     * */
    @Override
    public Long getActiveUserCount(Integer storeId, Date beginTime, Date endTime) {
        if (storeId > 0) {
            return mtUserActionMapper.getStoreActiveUserCount(storeId, beginTime, endTime);
        } else {
            return mtUserActionMapper.getActiveUserCount(beginTime, endTime);
        }
    }

    /**
     * 重置手机号
     *
     * @param  mobile 手机号码
     * @param  userId 会员ID
     * @throws BusinessCheckException
     */
    @Override
    public void resetMobile(String mobile, Integer userId) {
        if (mobile == null || StringUtil.isEmpty(mobile)) {
            return;
        }
        mtUserMapper.resetMobile(mobile, userId);
    }
}
