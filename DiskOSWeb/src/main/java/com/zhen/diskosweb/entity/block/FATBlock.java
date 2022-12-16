package com.zhen.diskosweb.entity.block;

import lombok.Data;
import lombok.ToString;

import java.util.Arrays;

/**
 *  FATBlock：存放FAT文件索引表的磁盘块
 *      序列化后字节数：1128 （差不多得了）
 */
@Data
@ToString
public class FATBlock extends BasicBlock{

    private int[] FATList = new int[256];

    /**
     *  构造函数：将所有文件索引值赋值为 -1
     */
    public FATBlock() {
        Arrays.fill(FATList, -1);
    }
}
