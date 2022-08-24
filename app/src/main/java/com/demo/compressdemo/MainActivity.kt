package com.demo.compressdemo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.provider.MediaStore
import android.text.TextUtils
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.arthenica.mobileffmpeg.Config
import com.arthenica.mobileffmpeg.FFmpeg
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private var videoWidth: Int = -1
    private var videoHeight: Int = -1
    private var videoRotation: Int = -1
    private var videoBitRate: Int = 800
    private var tvInputPath: TextView? = null
    private var tvOutPath: TextView? = null
    private var btOpenVideo: Button? = null
    private var btStartCompress: Button? = null
    private var compressVideoOutPath: String? = null
    private var inputPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        tvInputPath = findViewById(R.id.tv_input_path)
        tvOutPath = findViewById(R.id.tv_out_path)
        btOpenVideo = findViewById(R.id.bt_open_video)
        btStartCompress = findViewById(R.id.bt_start_compress)
        btOpenVideo?.setOnClickListener {
            chooseVideo()
        }
        btStartCompress?.setOnClickListener {
            if (TextUtils.isEmpty(inputPath)) {
                tvOutPath?.text = "请选择需要压缩的视频"
            } else {
                tvOutPath?.text = "压缩中..."
                compressVideo(inputPath!!)
            }
        }
        verifyPermissions()
    }

    private fun verifyPermissions() {
        val permissionStore = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        )
        val permission = ActivityCompat.checkSelfPermission(this@MainActivity, "android.permission.WRITE_EXTERNAL_STORAGE")
        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this@MainActivity, permissionStore, 3002)
        }
    }

    private fun chooseVideo() {
        val intent = Intent(
            Intent.ACTION_PICK,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
        startActivityForResult(intent, 100)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            100 -> {
                val uri = data!!.data;
                val proj = arrayOf(MediaStore.Images.Media.DATA)
                val resolver = this@MainActivity.contentResolver;
                val cursor = uri!!.let { resolver.query(it, proj, null, null, null, null) };
                cursor?.moveToFirst();
                val path = cursor?.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA));//获得文件路径
                val file = File(path)//获得视频
                inputPath = file.path
                tvInputPath?.text = "视频选择路径：\n $inputPath"
                tvOutPath?.text = "请开始压缩"
                getVideoInfo(file.path)
            }
        }
    }

    private fun compressVideo(path: String) {
        val command = getCommands(path)
        val rc = FFmpeg.execute(command)

        Log.d(TAG, "Command returnCode $rc")
        tvOutPath?.text = rc.toString()
        when (rc) {
            Config.RETURN_CODE_SUCCESS -> {
                Log.d(TAG, "Command execution completed successfully.")
                tvOutPath?.text = "压缩成功，压缩视频输出路径：\n $compressVideoOutPath"
                Log.d(TAG, "compress onFinish!")
            }
            Config.RETURN_CODE_CANCEL -> {
                tvOutPath?.text = "取消压缩!!"
            }
            else -> {
                tvOutPath?.text = "压缩失败!! \nrc $rc"
                Config.printLastCommandOutput(Log.INFO)
            }
        }
    }

    private fun getCommands(path: String): String {
        val newName = "${SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.getDefault()).format(System.currentTimeMillis())}_${((Math.random() * 9 + 1) * 1000).toInt()}"
        compressVideoOutPath = "/storage/emulated/0/Download/$newName.mp4"
        Log.d(TAG, "getCommands: newInputPath $compressVideoOutPath")
        Log.d(TAG, "getCommands: srcFile absolutePath $path")
        Log.d(TAG, "getCommands: videoWidth $videoWidth videoHeight $videoHeight")
        val bv = videoBitRate / 1024
        val width = when (videoRotation) {
            0, 180 -> videoWidth //横屏
            else -> videoHeight //竖屏
        }
        val height = when (videoRotation) {
            0, 180 -> videoHeight //横屏
            else -> videoWidth //竖屏
        }
        val s = toPixel(width, height)
        return " -i $path -b:v ${bv}k -s $s -crf 20 $compressVideoOutPath"
    }

    private fun getVideoInfo(path: String) {
        val retriver = MediaMetadataRetriever()
        retriver.setDataSource(path)
        videoWidth = retriver.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)!!.toInt()
        videoHeight = retriver.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)!!.toInt()
        videoRotation = retriver.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)!!.toInt()
        videoBitRate = retriver.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)!!.toInt()
        Log.d(TAG, "initData: videoWidth $videoWidth videoHeight $videoHeight videoRotation $videoRotation videoBitRate $videoBitRate ")
    }

    /**
     * 转换压缩像素
     * @param videoWidth 视频宽度
     * @param videoHeight 视频高度
     * @return ps:576x1024
     * */
    fun toPixel(videoWidth: Int, videoHeight: Int): String {
        return if (videoWidth > videoHeight) {
            computePixel(videoWidth, videoWidth, videoHeight)
        } else {
            computePixel(videoHeight, videoWidth, videoHeight)
        }
    }

    private fun computePixel(judge: Int, videoWidth: Int, videoHeight: Int): String {
        var newVW = 576
        var newVH = 1024
        if (judge <= 1280) {
            newVW = (videoWidth * 0.95).toInt()
            newVH = (videoHeight * 0.95).toInt()
            return "${newVW}x${newVH}"
        }
        if (judge <= 1920) {
            newVW = (videoWidth * 0.65).toInt()
            newVH = (videoHeight * 0.65).toInt()
            return "${newVW}x${newVH}"
        }
        if (judge <= 2048) {
            newVW = (videoWidth * 0.5).toInt()
            newVH = (videoHeight * 0.5).toInt()
            return "${newVW}x${newVH}"
        }
        if (judge <= 2560) {
            newVW = (videoWidth * 0.4).toInt()
            newVH = (videoHeight * 0.4).toInt()
            return "${newVW}x${newVH}"
        }
        if (judge <= 3200) {
            newVW = (videoWidth * 0.35).toInt()
            newVH = (videoHeight * 0.35).toInt()
            return "${newVW}x${newVH}"
        }
        if (judge <= 3840) {
            newVW = (videoWidth * 0.3).toInt()
            newVH = (videoHeight * 0.3).toInt()
            return "${newVW}x${newVH}"
        }
        if (judge <= 4096) {
            newVW = (videoWidth * 0.3).toInt()
            newVH = (videoHeight * 0.3).toInt()
            return "${newVW}x${newVH}"
        }
        if (judge <= 6400) {
            newVW = (videoWidth * 0.15).toInt()
            newVH = (videoHeight * 0.15).toInt()
            return "${newVW}x${newVH}"
        }
        return "${newVW}x${newVH}"
    }

    companion object {
        val TAG = "MainActivity"
    }
}