package com.lyk.priority_thread_pool

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.lyk.priority_thread_pool.thread.HiExecutor
import com.lyk.priority_thread_pool.ui.theme.PriorityThreadPoolTheme

class MainActivity : ComponentActivity() {
    private val TAG: String = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PriorityThreadPoolTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Greeting("Android")
                }
            }
        }

        testPriorityThreadPool()
    }


    // 这中方法无法让已经开始运行的线程停止，
    private fun testPriorityThreadPool() {
        Log.i(TAG, "HiExecutor start")
        HiExecutor.execute(1, Runnable {
            Log.i(TAG, "priority 1 sleep start")
            Thread.sleep(1000)
            Log.i(TAG, "priority 1 sleep end")
        })
        Log.i(TAG, "sleep 500")
        Thread.sleep(500)
        HiExecutor.pause()
        Log.i(TAG, "HiExecutor.pause()")
        Thread.sleep(2000)
        HiExecutor.resume()
        Log.i(TAG, "HiExecutor.resume()")
    }


    @Composable
    fun Greeting(name: String, modifier: Modifier = Modifier) {
        Text(
            text = "Hello $name!",
            modifier = modifier
        )
    }

    @Preview(showBackground = true)
    @Composable
    fun GreetingPreview() {
        PriorityThreadPoolTheme {
            Greeting("Android")
        }
    }
}