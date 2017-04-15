package com.javxu.naivephotodemo;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import java.io.File;

import static com.javxu.naivephotodemo.FileUtil.getOriginalUriFromFile;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    public static final int REQUEST_PERMISSION = 0;
    public static final int REQUEST_CAMERA = 1;
    public static final int REQUEST_GALLERY = 2;
    public static final int REQUEST_CROP = 3;

    private ImageButton mImageButton;

    private File mImageFile; //原始照片File,裁剪后会delete
    private Uri mImageUri;   //原始照片Uri
    private File mCropFile;  //裁剪照片File
    private Uri mCropUri;    //裁剪照片Uri

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        initRequestPermissions();
    }

    private void initView() {
        mImageButton = (ImageButton) findViewById(R.id.ib_photo);
        mImageButton.setOnClickListener(this);
    }

    private void initRequestPermissions() {
        // 应用已启动加载就开始申请权限，包括文件读写（要为照片准备文件）和相机拍照权限，这里由检查文件读写这一项权限触发，来申请两项权限
        String[] permissions = {Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA};
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, permissions, REQUEST_PERMISSION);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.ib_photo:
                new AlertDialog.Builder(this)
                        .setTitle("图片获取")
                        .setItems(new String[]{"相册", "拍照"}, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                switch (which) {
                                    case 0:
                                        if (initFileAndUriSuccess()) {
                                            toGallery();
                                        }
                                        break;
                                    case 1:
                                        if (initFileAndUriSuccess()) {
                                            toCamera();
                                        }
                                        break;
                                }
                            }
                        })
                        .create()
                        .show();
                break;
        }
    }

    private boolean initFileAndUriSuccess() {

        boolean flag = false;

        Long time = System.currentTimeMillis();

        // 路径 /storage/sdcard0/Pictures/Origin_1491973368473.jpg
        mImageFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Origin_" + time + ".jpg");
        mCropFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Crop_" + time + ".jpg");

        try {
            mImageFile.createNewFile();
            mCropFile.createNewFile();

            mImageUri = getOriginalUriFromFile(this, mImageFile); // 拍摄时使用，需要putExtra, 要FileProvider处理
            mCropUri = Uri.fromFile(mCropFile); // 裁剪输出时使用，目标uri，直接Uri.fromFile()

            flag = true;
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "为照片预备文件出错", Toast.LENGTH_SHORT).show();
        } finally {
            return flag;
        }
    }

    private void toCamera() {
        // 拍摄输出 需要 封装Uri
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        cameraIntent.putExtra("return-data", false);
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, mImageUri);

        startActivityForResult(cameraIntent, REQUEST_CAMERA);
    }

    private void toGallery() {
        // 相册返回封装Uri

        // 1.进入文件管理选择，返回的data包含的封装Uri形式为 /document/image:73 （7.0传入裁剪失败，不选用）
        //Intent galleryIntent = new Intent(Intent.ACTION_GET_CONTENT);

        // 2.单纯进入图库选择，返回的data包含的封装Uri形式为 /external/images/media/73 （选用做法）
        Intent galleryIntent = new Intent(Intent.ACTION_PICK);

        galleryIntent.setType("image/*");
        //galleryIntent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*"); //也可以

        //galleryIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(mImageFile)); // 并没有导出
        startActivityForResult(galleryIntent, REQUEST_GALLERY);
    }

    private void toCrop(Uri uri) {
        // 裁剪需要 特定封装Uri，输出则需要 原始Uri
        Intent cropIntent = new Intent("com.android.camera.action.CROP");

        cropIntent.setDataAndType(uri, "image/*"); // 这里的uri需要特定封装uri /external/images/media/24可以，/document/image:73不行

        cropIntent.putExtra("crop", "true");//设置裁剪
        /*cropIntent.putExtra("aspectX", 1);//裁剪宽高比例
        cropIntent.putExtra("aspectY", 1);*/

        cropIntent.putExtra("return-data", false);
        cropIntent.putExtra(MediaStore.EXTRA_OUTPUT, mCropUri); //裁剪输出uri直接Uri.fromFile(file)即可，高低版本都是如此

        startActivityForResult(cropIntent, REQUEST_CROP);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            // 防止选择相片或拍完照后点击取消而残存无效文件
            mImageFile.delete();
            mCropFile.delete();
            return;
        }
        switch (requestCode) {
            case REQUEST_CAMERA:
                //1.直接从已注入图像数据的 mImageUri 获取原始图像文件，不过要注意图像尺寸
                /*try {
                    //Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(mImageUri));
                    //mImageButton.setImageBitmap(bitmap);
                    //mImageButton.setImageURI(mImageUri);//或者直接这样也可以

                    int aimWidth1 = mImageButton.getWidth();
                    int aimHeigth1 = mImageButton.getHeight();
                    Bitmap bitmap1 = ImageUtil.getScaledBitmap(mImageFile.getAbsolutePath(), aimWidth1, aimHeigth1);
                    mImageButton.setImageBitmap(bitmap1);
                } catch (Exception e) {
                    e.printStackTrace();
                }*/

                //2.传递uri（由原始File经封装得到）去裁剪
                Uri uri_camera = FileUtil.getContentUriFromFile(MainActivity.this, mImageFile);
                toCrop(uri_camera);
                break;
            case REQUEST_GALLERY:
                Uri uri_gallery = data.getData(); // 已封装的uri
                // 可能是 ACTION_PICK 得来的         /external/images/media/24   可直接传递去裁剪
                // 可能是 ACTION_GET_CONTENT 得来的  /document/image:73          不可直接传递裁剪

                //1.直接从目标图像（已选择的原始文件）uri_gallery 获取 原始图像文件路径 再获取Bitmap，同样要注意图像尺寸
                //String path2 = FileUtil.getPathFromContentUri(MainActivity.this, uri_gallery);
                //Bitmap bitmap2 = BitmapFactory.decodeFile(path);
                //mImageButton.setImageBitmap(bitmap2);
                //mImageButton.setImageURI(uri_gallery);//或者直接这样也可以

                /*int aimWidth2 = mImageButton.getWidth();
                int aimHeigth2 = mImageButton.getHeight();
                Bitmap bitmap2 = ImageUtil.getScaledBitmap(path2, aimWidth2, aimHeigth2);
                mImageButton.setImageBitmap(bitmap2);*/

                //2.传递返回的照片 uri（已封装）去裁剪
                toCrop(uri_gallery);
                break;
            case REQUEST_CROP:
                try {
                    mImageFile.delete();
                    // 裁剪完成后，删除未经裁剪的文件，其中，相册选择得来是无效文件（因为选取时没putExtra("return-data"），拍摄的则是完整文件

                    //Bitmap bitmap3 = BitmapFactory.decodeStream(getContentResolver().openInputStream(mCropUri));
                    //mImageButton.setImageBitmap(bitmap3);
                    //mImageButton.setImageURI(mCropUri); //或者直接这样也可以

                    int aimWidth3 = mImageButton.getWidth();
                    int aimHeigth3 = mImageButton.getHeight();
                    Bitmap bitmap3 = ImageUtil.getScaledBitmap(mCropFile.getAbsolutePath(), aimWidth3, aimHeigth3);
                    mImageButton.setImageBitmap(bitmap3);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_PERMISSION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    return;
                } else {
                    Toast.makeText(MainActivity.this, "你拒绝了某些必要权限，所以不能进行相片操作", Toast.LENGTH_LONG).show();
                    mImageButton.setEnabled(false);
                }
                break;
        }
    }
}
