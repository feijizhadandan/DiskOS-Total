package com.zhen.diskosweb.entity.other;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

@Data
@AllArgsConstructor
public class User implements Serializable {

    private static final long serialVersionUID = -66003208557464574L;

    private String username;
    private String password;
    // 用户在根目录的权限
    private int permission;
    private boolean isRoot;
    private boolean isOnline;
}
