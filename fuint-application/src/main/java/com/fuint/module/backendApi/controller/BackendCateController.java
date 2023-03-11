package com.fuint.module.backendApi.controller;

import com.fuint.common.Constants;
import com.fuint.common.dto.AccountInfo;
import com.fuint.common.dto.GoodsCateDto;
import com.fuint.common.enums.StatusEnum;
import com.fuint.common.service.CateService;
import com.fuint.common.service.MerchantService;
import com.fuint.common.service.SettingService;
import com.fuint.common.service.StoreService;
import com.fuint.common.util.CommonUtil;
import com.fuint.common.util.TokenUtil;
import com.fuint.framework.exception.BusinessCheckException;
import com.fuint.framework.pagination.PaginationRequest;
import com.fuint.framework.pagination.PaginationResponse;
import com.fuint.framework.web.BaseController;
import com.fuint.framework.web.ResponseObject;
import com.fuint.repository.model.MtGoodsCate;
import com.fuint.repository.model.MtMerchant;
import com.fuint.repository.model.MtStore;
import com.fuint.utils.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品分类管理controller
 *
 * Created by FSQ
 * CopyRight https://www.fuint.cn
 */
@RestController
@RequestMapping(value = "/backendApi/goods/cate")
public class BackendCateController extends BaseController {

    /**
     * 商品分类服务接口
     */
    @Autowired
    private CateService cateService;

    /**
     * 配置服务接口
     * */
    @Autowired
    private SettingService settingService;

    /**
     * 商户接口
     */
    @Autowired
    private MerchantService merchantService;

    /**
     * 店铺服务接口
     */
    @Autowired
    private StoreService storeService;

    /**
     * 商品分类列表
     *
     * @param request
     * @return
     * @throws BusinessCheckException
     */
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    @CrossOrigin
    public ResponseObject list(HttpServletRequest request) throws BusinessCheckException {
        String token = request.getHeader("Access-Token");
        Integer page = request.getParameter("page") == null ? Constants.PAGE_NUMBER : Integer.parseInt(request.getParameter("page"));
        Integer pageSize = request.getParameter("pageSize") == null ? Constants.PAGE_SIZE : Integer.parseInt(request.getParameter("pageSize"));
        String name = request.getParameter("name");
        String status = request.getParameter("status");
        String merchantId = request.getParameter("merchantId") == null ? "" : request.getParameter("merchantId");
        String storeId = request.getParameter("storeId") == null ? "" : request.getParameter("storeId");

        AccountInfo accountInfo = TokenUtil.getAccountInfoByToken(token);
        if (accountInfo == null) {
            return getFailureResult(1001, "请先登录");
        }

        PaginationRequest paginationRequest = new PaginationRequest();
        paginationRequest.setCurrentPage(page);
        paginationRequest.setPageSize(pageSize);

        Map<String, Object> params = new HashMap<>();
        if (StringUtil.isNotEmpty(name)) {
            params.put("name", name);
        }
        if (StringUtil.isNotEmpty(status)) {
            params.put("status", status);
        }
        if (StringUtil.isNotEmpty(merchantId)) {
            params.put("merchantId", merchantId);
        }
        if (StringUtil.isNotEmpty(storeId)) {
            params.put("storeId", storeId);
        }
        if (accountInfo.getMerchantId() != null && accountInfo.getMerchantId() > 0) {
            params.put("merchantId", accountInfo.getMerchantId());
        }
        if (accountInfo.getStoreId() != null && accountInfo.getStoreId() > 0) {
            params.put("storeId", accountInfo.getStoreId());
        }
        paginationRequest.setSearchParams(params);
        paginationRequest.setSortColumn(new String[]{"sort asc", "status asc"});
        PaginationResponse<GoodsCateDto> paginationResponse = cateService.queryCateListByPagination(paginationRequest);

        Map<String, Object> param = new HashMap<>();
        param.put("status", StatusEnum.ENABLED.getKey());
        if (accountInfo.getMerchantId() != null && accountInfo.getMerchantId() > 0) {
            param.put("merchantId", accountInfo.getMerchantId());
        }
        List<MtMerchant> merchants = merchantService.queryMerchantByParams(param);
        List<MtStore> stores = storeService.queryStoresByParams(param);

        String imagePath = settingService.getUploadBasePath();

        Map<String, Object> result = new HashMap<>();
        result.put("paginationResponse", paginationResponse);
        result.put("imagePath", imagePath);
        result.put("merchants", merchants);
        result.put("stores", stores);

        return getSuccessResult(result);
    }

    /**
     * 删除分类
     *
     * @param request
     * @return
     */
    @RequestMapping(value = "/delete/{id}", method = RequestMethod.GET)
    public ResponseObject delete(HttpServletRequest request, @PathVariable("id") Integer id) throws BusinessCheckException {
        String token = request.getHeader("Access-Token");
        AccountInfo accountInfo = TokenUtil.getAccountInfoByToken(token);
        if (accountInfo == null) {
            return getFailureResult(1001, "请先登录");
        }

        String operator = accountInfo.getAccountName();
        cateService.deleteCate(id, operator);

        return getSuccessResult(true);
    }

    /**
     * 更新状态
     *
     * @return
     */
    @RequestMapping(value = "/updateStatus", method = RequestMethod.POST)
    @CrossOrigin
    public ResponseObject updateStatus(HttpServletRequest request, @RequestBody Map<String, Object> params) throws BusinessCheckException {
        String token = request.getHeader("Access-Token");
        String status = params.get("status") != null ? params.get("status").toString() : StatusEnum.ENABLED.getKey();
        Integer id = params.get("id") == null ? 0 : Integer.parseInt(params.get("id").toString());

        AccountInfo accountInfo = TokenUtil.getAccountInfoByToken(token);
        if (accountInfo == null) {
            return getFailureResult(1001, "请先登录");
        }

        MtGoodsCate mtCate = cateService.queryCateById(id);
        if (mtCate == null) {
            return getFailureResult(201, "该类别不存在");
        }

        String operator = accountInfo.getAccountName();

        MtGoodsCate cate = new MtGoodsCate();
        cate.setOperator(operator);
        cate.setId(id);
        cate.setStatus(status);
        cateService.updateCate(cate);

        return getSuccessResult(true);
    }

    /**
     * 保存商品分类
     *
     * @param request  HttpServletRequest对象
     * @return
     */
    @RequestMapping(value = "/save", method = RequestMethod.POST)
    public ResponseObject save(HttpServletRequest request, @RequestBody Map<String, Object> params) throws BusinessCheckException {
        String token = request.getHeader("Access-Token");
        String id = params.get("id") == null ? "" : params.get("id").toString();
        String name = params.get("name") == null ? "" : CommonUtil.replaceXSS(params.get("name").toString());
        String description = params.get("description") == null ? "" : CommonUtil.replaceXSS(params.get("description").toString());
        String logo = params.get("logo") == null ? "" : CommonUtil.replaceXSS(params.get("logo").toString());
        String sort = params.get("sort") == null ? "0" : params.get("sort").toString();
        String status = params.get("status") == null ? StatusEnum.ENABLED.getKey() : params.get("status").toString();
        String merchantId = params.get("merchantId") == null ? "" : params.get("merchantId").toString();
        String storeId = params.get("storeId") == null ? "" : params.get("storeId").toString();

        AccountInfo accountDto = TokenUtil.getAccountInfoByToken(token);
        if (accountDto == null) {
            return getFailureResult(1001, "请先登录");
        }
        if (accountDto.getMerchantId() != null && accountDto.getMerchantId() > 0) {
            merchantId = accountDto.getMerchantId().toString();
        }
        if (accountDto.getStoreId() != null && accountDto.getStoreId() > 0) {
            storeId = accountDto.getStoreId().toString();
        }

        MtGoodsCate info = new MtGoodsCate();
        info.setName(name);
        info.setDescription(description);
        info.setLogo(logo);
        info.setSort(Integer.parseInt(sort));
        info.setStatus(status);
        if (StringUtil.isNotEmpty(merchantId)) {
            info.setMerchantId(Integer.parseInt(merchantId));
        }
        if (StringUtil.isNotEmpty(storeId)) {
            info.setStoreId(Integer.parseInt(storeId));
        }

        String operator = accountDto.getAccountName();
        info.setOperator(operator);

        if (StringUtil.isNotEmpty(id)) {
            info.setId(Integer.parseInt(id));
            cateService.updateCate(info);
        } else {
            cateService.addCate(info);
        }

        return getSuccessResult(true);
    }

    /**
     * 分类详情
     *
     * @param request
     * @return
     */
    @RequestMapping(value = "/info/{id}", method = RequestMethod.GET)
    @CrossOrigin
    public ResponseObject info(HttpServletRequest request, @PathVariable("id") Integer id) throws BusinessCheckException {
        String token = request.getHeader("Access-Token");
        AccountInfo accountDto = TokenUtil.getAccountInfoByToken(token);
        if (accountDto == null) {
            return getFailureResult(1001, "请先登录");
        }

        MtGoodsCate mtCate = cateService.queryCateById(id);
        String imagePath = settingService.getUploadBasePath();

        Map<String, Object> result = new HashMap<>();
        result.put("cateInfo", mtCate);
        result.put("imagePath", imagePath);

        return getSuccessResult(result);
    }
}
