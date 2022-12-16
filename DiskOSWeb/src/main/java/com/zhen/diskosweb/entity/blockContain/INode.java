package com.zhen.diskosweb.entity.blockContain;

import com.zhen.diskosweb.entity.other.PermissionRecord;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serializable;

/**
 *  索引节点：
 *      用于存放文件的具体信息（除文件名外）
 *
 *      一个磁盘块存放 4个 inode 节点，那么需要拿出 20% 的磁盘块来存储 inode
 */
@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class INode implements Serializable {

    private static final long serialVersionUID = -6601903208557464574L;

    // 是否是文件夹
    private boolean isDir;
    // 文件的字节数
    private long fileSize;
    // 用户-执行权限读写权限记录
    private PermissionRecord permissions;
    // 文件数据Block的位置
    private int dataBlockLocation;

}
