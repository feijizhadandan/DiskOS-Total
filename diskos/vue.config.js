const { defineConfig } = require('@vue/cli-service')
module.exports = defineConfig({
  transpileDependencies: true,
  devServer: {
    open: true,
    host: 'localhost',
    port: 8080,
    https: false,
    //以上的ip和端口是我们本机的;下面为需要跨域的
    //配置跨域
    proxy: {
        '/api': {
            target: 'http://localhost:8888/',//这里后台的地址模拟的;应该填写你们真实的后台接口
            ws: true,       
            changOrigin: true,//允许跨域
            pathRewrite: {
                '^/api': '' //将所有含/api路径的，去掉/api转发给服务器
            }
        }
        
    }
}
})
