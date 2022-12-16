<template>
  <el-row :gutter="10">
    <el-col :xs="8" :sm="6" :md="4" :lg="1" :xl="1"><div class="grid-content"></div></el-col>
    <el-col :xs="4" :sm="6" :md="8" :lg="8" :xl="8">
      <div class="grid-content-command bg-purple-light">
        <el-container>
          <el-main id="command-area">
            <el-form ref="form" label-width="80px" @submit.enter.prevent @keydown.enter="onKeydownEnter">
              <el-form-item label="Command">
                <el-input ref="" v-model="inputCommand"></el-input>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="submitCommand">submit</el-button>
              </el-form-item>
            </el-form>
          </el-main>
        </el-container>
      </div>
    </el-col>
    <el-col :xs="4" :sm="6" :md="8" :lg="14" :xl="14">
      <div class="grid-content-result bg-purple-light">
        <el-input ref="resultBox" type="textarea" :rows="40" v-model="resultArea" resize="none" :readonly="true" id="result-area"></el-input>
      </div>
    </el-col>
    <el-col :xs="8" :sm="6" :md="4" :lg="1" :xl="1"><div class="grid-content"></div></el-col>
  </el-row>
</template>

<script setup>
import { ref, nextTick } from 'vue';
import { inject } from 'vue';

// 注入全局 axios
const axios = inject('axios');

// 用户名
const username = ref('');
// 密码
const password = ref('');
const code = ref();

// 输入的指令内容
const inputCommand = ref('');
// 提示符
const tips = ref('请输入账号：');
// 结果字符串
const resultArea = ref(tips.value);

// 标记登录状态
const isLogin = ref(false);
// 当前是输入账号还是密码
const inputPassword = ref(false);

// 是否是确认删除输入
const confirmDelete = ref(false);
// 用来记录删除的路径
const deletePath = ref('');

// 用来记录上一条后端返回的 status（如果是 CONFIRM_DELETE_DIR--12，则需要进行特殊处理）
// const lastStatus = ref('');
// result-area 的 dom
// const resultBox = ref(null); 不知道为什么不行，理论上是响应式的，但是找不到 scrollTop 属性

// 提交指令函数
function submitCommand() {
  resultArea.value += inputCommand.value + '\n';

  // 已登录
  if (isLogin.value) {
    // 清屏
    if (inputCommand.value === 'clear') {
      resultArea.value = tips.value;
      inputCommand.value = '';
    } else {
      // 如果这次应该输入是否删除
      if (confirmDelete.value) {
        // 确认删除才发送axios
        if (inputCommand.value === 'yes') {
          axios
            .post('/api/disk', {
              code: code.value,
              input: 'directremove ' + deletePath.value,
            })
            .then(res => {
              resultArea.value += res.data.msg + '\n';
              resultArea.value += tips.value;
              // 将滚轮移动至底部
              toLowest();
            });
        }
        // 不删除
        else {
          resultArea.value += '取消删除' + '\n' + '\n';
          resultArea.value += tips.value;
          // 将滚轮移动至底部
          toLowest();
        }
        inputCommand.value = '';
        confirmDelete.value = false;
      } else {
        axios
          .post('/api/disk', {
            code: code.value,
            input: inputCommand.value,
          })
          .then(res => {
            resultArea.value += res.data.msg + '\n';
            // 如果是注销成功
            if (res.data.status === 15) {
              username.value = '';
              password.value = '';
              isLogin.value = false;
              tips.value = '请输入账号：';
            }
            // 如果是确认删除指令，则需要修改变量
            if (res.data.status === 12) {
              confirmDelete.value = true;
            }
            resultArea.value += tips.value;
            // 将滚轮移动至底部
            toLowest();
          });
        // 存储删除路径（每次都存吧，没啥影响）
        deletePath.value = inputCommand.value.split(' ')[1];
        inputCommand.value = '';
      }
    }
  }
  // 未登录
  else {
    // 输入账号后，输入密码
    if (!inputPassword.value) {
      // 保存用户名
      username.value = inputCommand.value;
      inputPassword.value = true;
      tips.value = '请输入密码：';
      resultArea.value += '\n';
      resultArea.value += tips.value;
      inputCommand.value = '';
      toLowest();
    }
    // 输入密码后，提交信息
    else {
      // 保存密码
      password.value = inputCommand.value;
      inputPassword.value = false;
      axios
        .post('/api/disk/login', {
          username: username.value,
          password: password.value,
        })
        .then(res => {
          // 登录成功
          if (res.data.status == 1) {
            isLogin.value = true;
            resultArea.value += '登录成功' + '\n';
            tips.value = username.value + '@localhost:~$ ';
            resultArea.value += '\n';
            resultArea.value += tips.value;
            inputCommand.value = '';
            code.value = res.data.msg;
            toLowest();
          }
          // 登录失败
          else {
            resultArea.value += res.data.msg + '\n';
            tips.value = '请输入账号：';
            resultArea.value += '\n';
            resultArea.value += tips.value;
            inputCommand.value = '';
            inputPassword.value = false;
            toLowest();
          }
        });
    }
  }
}

function toLowest() {
  nextTick(() => {
    const resultBox = document.getElementById('result-area');
    resultBox.scrollTop = resultBox.scrollHeight;
  });
}

// 表单监听 Enter 按键
function onKeydownEnter(e) {
  var keyCode = window.event ? e.keyCode : e.which;
  if (keyCode == 13) {
    submitCommand(); // 回车也提交表单
  }
}
</script>

<style>
.el-col {
  border-radius: 4px;
}
.bg-purple-light {
  background: #e5e9f2;
}
.grid-content-command {
  border-radius: 4px;
  min-height: 36px;
  margin-top: 300px;
}
.grid-content-result {
  border-radius: 4px;
  min-height: 36px;
  margin-top: 30px;
  background-color: black;
}
#result-area {
  background-color: rgb(20, 20, 20);
  color: white;
}
</style>
