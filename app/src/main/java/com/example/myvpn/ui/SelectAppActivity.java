package com.example.myvpn.ui;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myvpn.R;

import java.util.ArrayList;
import java.util.List;

public class SelectAppActivity extends AppCompatActivity {

    private static final String TAG = "com.example.myvpn.selectapp";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_app);
        //getActionBar().setDisplayHomeAsUpEnabled(true);

        final PackageManager pm = getPackageManager();
        //get a list of installed apps.
        List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);

        ArrayList<String> selectpackages = getIntent().getStringArrayListExtra("SelectedPackages");
        //Log.e("ERROR", "Create Select App Activity with input packages size " + selectpackages.size());
        ((ListView)findViewById(R.id.listview_app)).setAdapter(new SelectAppListAdapter(this, packages, selectpackages));
    }



    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        View listitem;
        switch (item.getItemId()){
            case android.R.id.home:
                ArrayList<String> SelectedPackages = new ArrayList<String>();
                ListView listview = (ListView)findViewById(R.id.listview_app);
                for (int i=0; i<listview.getChildCount(); i++) {
                    listitem = listview.getChildAt(i);
                    CheckBox box = (CheckBox) listitem.findViewById(R.id.button_info);
                    TextView content = (TextView) listitem.findViewById(R.id.item_content);
                    if (box.isChecked()) {
                        SelectedPackages.add(content.getText().toString());
                        //Log.e("ERROR", "selectappactivity for "+content.getText().toString());
                    }

                }
                Intent intent = new Intent();
                intent.putStringArrayListExtra("SelectedPackages", SelectedPackages);
                setResult(RESULT_OK, intent);
                super.onBackPressed();
                break;
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        ArrayList<String> SelectedPackages = new ArrayList<String>();
        ListView listview = (ListView)findViewById(R.id.listview_app);
        for (int i=0; i<listview.getChildCount(); i++) {
            View listitem = listview.getChildAt(i);
            CheckBox box = (CheckBox) listitem.findViewById(R.id.button_info);
            TextView content = (TextView) listitem.findViewById(R.id.item_content);
            if (box.isChecked()) {
                SelectedPackages.add(content.getText().toString());
                //Log.e("ERROR", "selectappactivity for "+content.getText().toString());
            }

        }
        Intent intent = new Intent();
        intent.putStringArrayListExtra("SelectedPackages", SelectedPackages);
        setResult(RESULT_OK, intent);
        super.onBackPressed();
    }

    class SelectAppListAdapter extends BaseAdapter {
        private List<ApplicationInfo> dataList;
        private Context context;
        private List<String> selectedString;

        public SelectAppListAdapter(Context context, List<ApplicationInfo> dataList,
                         List<String> selectedStr) {
            this.context = context;
            this.dataList = dataList;
            this.selectedString = selectedStr;

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
            CheckBox box = (CheckBox) view.findViewById(R.id.button_info);
            //组件一拿到，开始组装
            title_view.setText(dataList.get(position).loadLabel(getPackageManager()).toString());
            content_view.setText(dataList.get(position).packageName);
            //itemImg.setBackgroundResource(icons[position]);
            itemImg.setBackground(getPackageManager().getApplicationIcon(dataList.get(position)));
            for (int i=0; i<selectedString.size(); i++)
            {
                //Log.e("ERROR", "try checked for "+selectedString.get(i)+"," + dataList.get(position).packageName);
                if (selectedString.get(i).equals(dataList.get(position).packageName)) {
                    box.setChecked(true);
                    //Log.e("ERROR", "set checked for "+selectedString.get(i));
                }

            }
            //组装玩开始返回
            return view;
        }

    }

}

