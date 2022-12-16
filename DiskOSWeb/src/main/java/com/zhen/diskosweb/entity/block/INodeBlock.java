package com.zhen.diskosweb.entity.block;

import com.zhen.diskosweb.entity.blockContain.INode;
import lombok.Data;
import lombok.ToString;

import java.util.Arrays;

/**
 *  INodeBlock: 用于存 INode 节点的磁盘块
 *      序列化后占 726 字节，还有拓展性
 */
@Data
@ToString
public class INodeBlock extends BasicBlock{

    // 标记该 inode 磁盘块是否已经存满
    private boolean isFull = false;

    private INode[] iNodeList = new INode[4];

    /**
     *  构造函数：将 iNodeList 都赋值为空对象
     */
    public INodeBlock() {
        Arrays.fill(iNodeList, null);
    }

    /**
     * 获取该 inode 块中的空位
     * @return 空位下标
     */
    public int getEmptyIndex() {
        for(int i = 0; i < 4; i ++)
            if (iNodeList[i] == null) return i;
        return -1;
    }

    /**
     * 往 inode 数组存入 inode对象
     * @param index 数组下标
     * @param iNode 存入的 inode 对象
     */
    public void setINode(int index, INode iNode) {
        iNodeList[index] = iNode;
    }

}
