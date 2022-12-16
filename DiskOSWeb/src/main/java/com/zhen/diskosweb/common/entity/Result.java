package com.zhen.diskosweb.common.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data@AllArgsConstructor
public class Result {

    private int status;
    private Object data;

}
