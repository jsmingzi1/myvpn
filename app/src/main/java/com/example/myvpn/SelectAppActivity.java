package com.example.myvpn;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

public class SelectAppActivity extends AppCompatActivity {

    private static final String TAG = "com.example.myvpn.selectapp";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_app);


        final PackageManager pm = getPackageManager();
        //get a list of installed apps.
        List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);

        ((ListView)findViewById(R.id.listapp)).setAdapter(new SelectAppListAdapter(this, packages, 0));
        /*
        for (ApplicationInfo packageInfo : packages) {

            Log.d(TAG, "Installed package :" + packageInfo.loadLabel(getPackageManager()).toString() + "," + packageInfo.packageName);
            Log.d(TAG, "Source dir : " + packageInfo.sourceDir);
            Log.d(TAG, "Launch Activity :" + pm.getLaunchIntentForPackage(packageInfo.packageName));

        }*/
    }


    class SelectAppListAdapter extends BaseAdapter {
        private List<ApplicationInfo> dataList;
        private Context context;
        private int resource;

        public SelectAppListAdapter(Context context, List<ApplicationInfo> dataList,
                         int resource) {
            this.context = context;
            this.dataList = dataList;
            this.resource = resource;

        }


        @Override
        public int getCount() {
            return dataList.size();
        }

        @Override
        public Object getItem(int position) {
            return dataList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {//组装数据
            View view = View.inflate(SelectAppActivity.this, R.layout.list_view, null);//在list_item中有两个id,现在要把他们拿过来
            TextView title_view = (TextView) view.findViewById(R.id.item_title);
            TextView content_view = (TextView) view.findViewById(R.id.item_content);
            ImageView itemImg = (ImageView) view.findViewById(R.id.item_img);
            //组件一拿到，开始组装
            title_view.setText(dataList.get(position).loadLabel(getPackageManager()).toString());
            content_view.setText(dataList.get(position).packageName);
            //itemImg.setBackgroundResource(icons[position]);
            itemImg.setBackground(getPackageManager().getApplicationIcon(dataList.get(position)));
            //组装玩开始返回
            return view;
        }

    }

}

