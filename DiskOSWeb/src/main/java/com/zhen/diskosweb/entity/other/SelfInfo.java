package com.zhen.diskosweb.entity.other;

import com.zhen.diskosweb.entity.block.data.FCBBlock;
import lombok.Data;

import java.io.Serializable;

/**
 * 每个线程的特色信息，决定用户在系统中的操作结果
 */
@Data
public class SelfInfo implements Serializable {
    // 当前所在路径（初始化是根目录路径）
    private String nowPath;
    // 当前路径文件夹的 FCB 磁盘块（初始化是根目录的 FCB磁盘块）
    private FCBBlock nowFCBBlock;
    // 当前登录者
    private User nowUser;
    // 当前目录的权限
    private int nowPermission;
}
