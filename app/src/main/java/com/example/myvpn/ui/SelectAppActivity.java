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
import android.widget.SearchView;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myvpn.R;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SelectAppActivity extends AppCompatActivity {

    private static final String TAG = "com.example.myvpn.selectapp";
    public boolean isShowAll = false;
    public String searchString = "";
    public ListView listView = null;
    public SearchView searchView = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
        setContentView(R.layout.activity_select_app);
        //getActionBar().setDisplayHomeAsUpEnabled(true);

        //getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.selectapptitlelayout);

        resetListView();
        initSearchView();
        findViewById(R.id.switch2).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SelectAppActivity.this.isShowAll = ((Switch) v).isChecked();
                SelectAppActivity.this.listView.invalidateViews();
            }
        });
    }

    public void resetListView() {
        final PackageManager pm = getPackageManager();
        //get a list of installed apps.
        List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        ArrayList<String> selectpackages = getIntent().getStringArrayListExtra("SelectedPackages");
        listView = (ListView)findViewById(R.id.listview_app);
        listView.setAdapter(new SelectAppListAdapter(this, packages, selectpackages));
    }

    private void initSearchView() {
        searchView= (SearchView) findViewById(R.id.shapeListSearchView);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                searchString = newText;
                SelectAppActivity.this.listView.invalidateViews();
                return false;
            }
        });
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
            int count = 0;
            Iterator<ApplicationInfo> it = dataList.iterator();
            while (it.hasNext()) {
                ApplicationInfo ai = it.next();
                boolean bSystem = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                if (ai.packageName.equals(SelectAppActivity.this.getPackageName())) {
                    continue; //filter out itself
                }

                if (SelectAppActivity.this.searchString.length() > 0 && ai.packageName.toLowerCase().contains(SelectAppActivity.this.searchString.toLowerCase())==false)
                    continue;
                if (SelectAppActivity.this.isShowAll==false && bSystem == true)
                    continue;

                count++;
            }
            //Log.e("total count is ", "value is "+count);
            return count;
        }

        @Override
        public Object getItem(int position) {
            int count = 0;
            Iterator<ApplicationInfo> it = dataList.iterator();
            while (it.hasNext()) {
                ApplicationInfo ai = it.next();
                boolean bSystem = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                if (ai.packageName.equals(SelectAppActivity.this.getPackageName())) {
                    continue; //filter out itself
                }

                if (SelectAppActivity.this.searchString.length() > 0 && ai.packageName.toLowerCase().contains(SelectAppActivity.this.searchString.toLowerCase())==false)
                    continue;
                if (SelectAppActivity.this.isShowAll==false && bSystem == true)
                    continue;

                if (count == position)
                    return ai;
                //else if (count > position) {
                //    Log.e("return is null", "count is bigger than position: "+count + ","+position);
                //}
                count++;
            }
            //Log.e("return is null", "position is "+position + ", total is "+getCount() + ", current count is "+count);
            return null;
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
            ApplicationInfo ai = (ApplicationInfo)getItem(position);
            //if (ai == null) {
            //    Log.e("assert detail", "position is " + position + ", total count is " + getCount());
            //}
            //assert(ai!=null);
            //组件一拿到，开始组装
            //Log.e("ai title view is", ai.loadLabel(getPackageManager()).toString());
            title_view.setText(ai.loadLabel(getPackageManager()).toString());
            title_view.setMaxWidth(800);
            title_view.setMaxLines(1);
            content_view.setText(ai.packageName);
            //itemImg.setBackgroundResource(icons[position]);
            itemImg.setBackground(getPackageManager().getApplicationIcon(ai));
            for (int i=0; i<selectedString.size(); i++)
            {
                //Log.e("ERROR", "try checked for "+selectedString.get(i)+"," + dataList.get(position).packageName);
                if (selectedString.get(i).equals(ai.packageName)) {
                    box.setChecked(true);
                    //Log.e("ERROR", "set checked for "+selectedString.get(i));
                }

            }
            //组装玩开始返回
            return view;
        }

    }

}

