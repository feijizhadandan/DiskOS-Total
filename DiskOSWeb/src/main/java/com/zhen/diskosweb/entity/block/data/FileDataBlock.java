package com.zhen.diskosweb.entity.block.data;

import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class FileDataBlock extends DataBlock{

    private byte[] data;

}
