package com.zhen.diskosweb.entity.block.data;


import com.zhen.diskosweb.entity.blockContain.FCB;
import lombok.Data;
import lombok.ToString;

/**
 *  一个FCBBlock最多存放40个子文件的FCB
 *      序列化后占用内存大概 800+
 */
@Data
@ToString
public class FCBBlock extends DataBlock {

    // 文件夹的数据块存储子文件的FCB，
    FCB[] FCBList = new FCB[40];

    public int getFreeIndex() {
        for (int i = 0; i < FCBList.length; i++) {
            if (FCBList[i] == null) return i;
        }
        // 表示子文件FCB数量已到达上限
        return -1;
    }

    public void addFCB(FCB fcb) {
        int freeIndex = getFreeIndex();
        FCBList[freeIndex] = fcb;
    }
}
