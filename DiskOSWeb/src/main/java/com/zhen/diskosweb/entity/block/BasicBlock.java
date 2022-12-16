package com.zhen.diskosweb.entity.block;

import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

/**
 *  磁盘块（基类型）：
 *      提供一些磁盘块操作的通用方法和数据
 *
 *  所有的数据都应该被保存在磁盘块中：
 *      1、SuperBlock    （1块）
 *
 *      2、集中的inode    （25600块）
 *
 *      3、bitMap：位示图，记录空闲盘块     （100块）
 *          java没有无符号byte型，因此使用"字节示图"：1个字节表示一个盘块的使用情况
 *          102400(块) * 1(byte) = 102400(byte); 102400(byte) / 1024(byte/块) = 100块
 *
 *      4、FAT 文件分配表：记录 a号盘块的下一块盘号       （400块）
 *          除去 inode节点后剩余 102400(块); 使用int类型的数组存储：102400(块) * 4(byte) = 409600(byte)
 *          409600(byte) / 1024 = 400(块)
 *
 *      5、数据内容
 *
 */
@Data
@ToString
public class BasicBlock implements Serializable {

    private static final long serialVersionUID = -66019032085464574L;

}
