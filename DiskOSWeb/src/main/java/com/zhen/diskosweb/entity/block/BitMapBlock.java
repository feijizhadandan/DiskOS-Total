package com.zhen.diskosweb.entity.block;

import lombok.Data;
import lombok.ToString;

/**
 *  BitMapBlock：用于存储位示数组
 *      序列化后占 1134 字节 (差不多得了)
 */
@Data
@ToString
public class BitMapBlock extends BasicBlock{

    private boolean[] bitMapList = new boolean[1024];

}
