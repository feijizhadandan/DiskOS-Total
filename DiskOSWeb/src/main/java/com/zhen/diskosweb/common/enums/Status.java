package com.zhen.diskosweb.common.enums;

public class Status {

    /**
     * 路径有误
     */
    public static final int PATH_ERROR = 0;

    /**
     * 操作成功
     */
    public static final int SUCCESS = 1;

    /**
     * 存在同名文件/文件夹
     */
    public static final int REPEAT_NAME_ERROR = 2;

    /**
     * 文件不存在
     */
    public static final int FILE_NOT_EXIST_ERROR = 3;

    /**
     * 空闲数据块已用尽
     */
    public static final int NO_FREE_DATA_BLOCK_ERROR = 4;

    /**
     * 文件夹不可以写入数据
     */
    public static final int DIR_CAN_NOT_WRITE_ERROR = 5;

    /**
     * 文件夹不能通过删除文件的指令删除
     */
    public static final int DIR_CAN_NOT_DELETE_AS_FILE = 6;

    /**
     * 文件不能通过删除文件夹的指令删除
     */
    public static final int FILE_CAN_NOT_DELETE_AS_DIR = 7;

    /**
     * 未找到该名称的文件夹
     */
    public static final int DIR_NOT_EXIST_ERROR = 8;

    /**
     * 文件夹删除失败
     */
    public static final int DIR_DELETE_FAIL_ERROR = 9;

    /**
     *  外部文件打开失败
     */
    public static final int EXTERNAL_FILE_OPEN_ERROR = 10;

    /**
     *  指令输入有误
     */
    public static final int COMMAND_ERROR = 10;

    /**
     *  文件夹不可被读取
     */
    public static final int DIR_CAN_NOT_CAT = 11;

    /**
     *  确定是否删除文件夹
     */
    public static final int CONFIRM_DELETE_DIR = 12;

    /**
     * 权限不足
     */
    public static final int PERMISSION_ERROR = 13;

    /**
     * 登录失败，账号或密码错误
     */
    public static final int LOGIN_FAIL = 14;

    /**
     * 注销成功
     */
    public static final int LOGOUT_SUCCESS = 15;

}
