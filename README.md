# 将tensorflow训练好的模型移植到android上


## 说明
本文将描述如何将一个训练好的模型植入到android设备上，并且在android设备上输入待处理数据，通过模型，获取输出数据。
通过一个例子，讲述整个移植的过程。（demo的源码访问github上了https://github.com/CrystalChen1017/TSFOnAndroid）
整体的思路如下：
1. 使用python在PC上训练好你的模型，保存为pb文件
2. 新建android project，把pb文件放到assets文件夹下
3. 将tensorflow的so文件以及jar包放到libs下
4. 加载库文件，让tensorflow在app中运行起来

### 准备
1. tensorflow的环境，参阅http://blog.csdn.net/cxq234843654/article/details/70857562
2. libtensorflow_inference.so
3. libandroid_tensorflow_inference_java.jar
4. 如果要自己编译得到以上两个文件，需要安装bazel。参阅http://blog.csdn.net/cxq234843654/article/details/70861155 的第2步

以上两个文件通过以下两个网址进行下载：
https://github.com/CrystalChen1017/TSFOnAndroid/tree/master/app/libs
或者
http://download.csdn.net/detail/cxq234843654/9833372

### PC端模型的准备
这是一个很简单的模型，输入是一个数组matrix1，经过操作后，得到这个数组乘以2*matrix1。

1. 给输入数据命名为`input`,在android端需要用这个`input`来为输入数据赋值
2. 给输输数据命名为`output`,在android端需要用这个`output`来为获取输出的值
3. 不能使用 tf.train.write_graph()保存模型，因为它只是保存了模型的结构，并不保存训练完毕的参数值
4. 不能使用 tf.train.saver()保存模型，因为它只是保存了网络中的参数值，并不保存模型的结构。
5. `graph_util.convert_variables_to_constants`可以把整个sesion当作常量都保存下来，通过`output_node_names`参数来指定输出
6. `tf.gfile.FastGFile('model/cxq.pb', mode='wb')`指定保存文件的路径以及读写方式
7. `f.write（output_graph_def.SerializeToString()）`将固化的模型写入到文件


```python
# -*- coding:utf-8 -*-
import tensorflow as tf
from tensorflow.python.client import graph_util

session = tf.Session()

matrix1 = tf.constant([[3., 3.]], name='input')
add2Mat = tf.add(matrix1, matrix1, name='output')

session.run(add2Mat)

output_graph_def = graph_util.convert_variables_to_constants(session, session.graph_def,output_node_names=['output'])

with tf.gfile.FastGFile('model/cxq.pb', mode='wb') as f:
    f.write(output_graph_def.SerializeToString())

session.close()

```

运行后就会在model文件夹下产生一个cxq.pb文件，现在这个文件将刚才一系列的操作固化了，因此下次需要计算变量乘2时，我们可以直接拿到pb文件，指定输入，再获取输出。


### （可选的）bazel编译出so和jar文件
如果希望自己通过tensorflow的源码编译出so和jar文件，则需要通过终端进入到tensorflow的目录下，进行如下操作：

1. 编译so库

```bash
bazel build -c opt //tensorflow/contrib/android:libtensorflow_inference.so \
    -- crosstool_top=//external:android/crosstool \
    -- host_crosstool_top=@bazel_tools//tools/cpp:toolchain \
    -- cpu=armeabi-v7a
```

编译完毕后，libtensorflow_inference.so的路径为：    
/tensorflow/bazel-bin/tensorflow/contrib/android

2. 编译jar包

```bash
bazel build //tensorflow/contrib/android:android_tensorflow_inference_java

```

编译完毕后，android_tensorflow_inference_java.jar的路径为：    
/tensorflow/bazel-bin/tensorflow/contrib/android


### android端的准备
1. 新建一个Android Project
2. 把刚才的pb文件存放到assets文件夹下
3. 将libandroid_tensorflow_inference_java.jar存放到/app/libs目录下，并且右键“add as Libary”
4. 在/app/libs下新建armeabi文件夹，并将libtensorflow_inference.so放进去

### 配置app:gradle以及gradle.properties

1. 在android节点下添加soureSets，用于制定jniLibs的路径

```groovy
sourceSets {
        main {
            jniLibs.srcDirs = ['libs']
        }
    }
```

2. 在defaultConfig节点下添加

```groovy
defaultConfig {

        ndk {
            abiFilters "armeabi"
        }
    }
```

3. 在gradle.properties中添加下面一行

```
android.useDeprecatedNdk=true
```

通过以上3步操作，tensorflow的环境已经部署好了。

### 模型的调用

我们先新建一个MyTSF类，在这个类里面进行模型的调用，并且获取输出

```java
package com.learn.tsfonandroid;

import android.content.res.AssetManager;
import android.os.Trace;

import org.tensorflow.contrib.android.TensorFlowInferenceInterface;


public class MyTSF {
    private static final String MODEL_FILE = "file:///android_asset/cxq.pb"; //模型存放路径

    //数据的维度
    private static final int HEIGHT = 1;
    private static final int WIDTH = 2;

    //模型中输出变量的名称
    private static final String inputName = "input";
    //用于存储的模型输入数据
    private float[] inputs = new float[HEIGHT * WIDTH];

    //模型中输出变量的名称
    private static final String outputName = "output";
    //用于存储模型的输出数据
    private float[] outputs = new float[HEIGHT * WIDTH];



    TensorFlowInferenceInterface inferenceInterface;


    static {
        //加载库文件
        System.loadLibrary("tensorflow_inference");
    }

    MyTSF(AssetManager assetManager) {
        //接口定义
        inferenceInterface = new TensorFlowInferenceInterface(assetManager,MODEL_FILE);
    }

    public float[] getAddResult() {
        //为输入数据赋值
        inputs[0]=1;
        inputs[1]=3;

        //将数据feed给tensorflow
        Trace.beginSection("feed");
        inferenceInterface.feed(inputName, inputs, WIDTH, HEIGHT);
        Trace.endSection();

        //运行乘2的操作
        Trace.beginSection("run");
        String[] outputNames = new String[] {outputName};
        inferenceInterface.run(outputNames);
        Trace.endSection();

        //将输出存放到outputs中
        Trace.beginSection("fetch");
        inferenceInterface.fetch(outputName, outputs);
        Trace.endSection();

        return outputs;
    }


}


```

在Activity中使用MyTSF类

```java
 public void click01(View v){
        Log.i(TAG, "click01: ");
        MyTSF mytsf=new MyTSF(getAssets());
        float[] result=mytsf.getAddResult();
        for (int i=0;i<result.length;i++){
            Log.i(TAG, "click01: "+result[i] );
        }

    }
```


















