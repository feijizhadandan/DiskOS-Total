package com.zhen.diskosweb.common.entity;

import lombok.Data;

import java.io.Serializable;

/**
 * 对文件操作后返回的统一类型
 */
@Data
public class ServiceResult implements Serializable {

    private static final long serialVersionUID = 101L;

    /**
     * 状态码
     */
    public int status;

    /**
     * 响应字符串
     */
    public String msg;
    /**
     * 初始化一个 ServiceResult 对象
     * @param status 状态码
     * @param msg 响应字符串
     */
    public ServiceResult(int status, String msg)
    {
        this.status = status;
        this.msg = msg;
    }

}
