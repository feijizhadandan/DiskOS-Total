package com.zhen.diskosweb.entity.blockContain;

import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

/**
 *  文件目录信息：
 *      FCB = 文件名 + inode编号
 */
@Data
@ToString
public class FCB implements Serializable {

    // 文件名(只支持英文, 上限20个字符, 即20字节)
    private String fileName;
    // inode索引节点位置标号(4字节)
    private int iNodeIndex;

    public FCB(String fileName, int iNodeIndex) {
        this.fileName = fileName;
        this.iNodeIndex = iNodeIndex;
    }
}
