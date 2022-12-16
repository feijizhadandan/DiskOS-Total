package com.zhen.diskosweb.entity.block;

import com.zhen.diskosweb.entity.blockContain.FCB;
import lombok.Data;

/**
 *  超级块：（用于记录磁盘块的总体情况）
 *      1、总磁盘块数
 *      2、inode区的大小、范围
 *      3、BitMap（位示表）的位置
 *      4、FAT（文件分配表）的位置
 *      5、根目录的 FCB
 */
@Data
public class SuperBlock extends BasicBlock {

    // 总磁盘块数量
    public final int TOTAL_BLOCK_COUNT = 102400;
    // 超级块位置
    public final int SUPER_BLOCK_INDEX = 0;
    // inode块起始位置
    public final int INODE_BLOCK_START_INDEX = 1;
    // inode块终止位置
    public final int INODE_BLOCK_END_INDEX = 25600;
    // BitMap块起始位置
    public final int BITMAP_BLOCK_START_INDEX = 25601;
    // BitMap块终止位置
    public final int BITMAP_BLOCK_END_INDEX = 25700;
    // BitMap块：用于记录 INodeBlock 的空闲情况的块数
    public final int BITMAP_BLOCK_INODE_CNT = 25;
    // BitMap块：用于记录 DataBlock 的空闲情况的块数
    public final int BITMAP_BLOCK_DATA_CNT = 74;
    // FAT块起始位置
    public final int FAT_BLOCK_START_INDEX = 25701;
    // FAT块终止位置
    public final int FAT_BLOCK_END_INDEX = 26100;
    // 数据块起始位置
    public final int DATA_BLOCK_START_INDEX = 26101;
    // 数据块终止位置
    public final int DATA_BLOCK_END_INDEX = 102399;

    // inode编号终点
    public final int INODE_START_INDEX = 102400;

    // bitMapBlock的存储容量
    public final int BITMAP_BLOCK_SIZE = 1024;

    private FCB rootFCB;

    public SuperBlock() {
        rootFCB = new FCB("/", 0);
    }


}
