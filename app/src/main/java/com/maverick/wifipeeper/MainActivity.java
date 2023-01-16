/*
 * Copyright (c) 2023-present MaverickZhao<479155558@qq.com>
 * Based on work by
 * Copyright (c) 2016-present 贵州纳雍穿青人李裕江<1032694760@qq.com>
 *
 * The software is licensed under the Mulan PSL v2.
 * You can use this software according to the terms and conditions of the Mulan PSL v2.
 * You may obtain a copy of Mulan PSL v2 at:
 *     http://license.coscl.org.cn/MulanPSL2
 * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR FIT FOR A PARTICULAR
 * PURPOSE.
 * See the Mulan PSL v2 for more details.
 */
package com.maverick.wifipeeper;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private TextView mHeaderHint;
    private ArrayList<WifiInfo> mWifiInfoList;
    private MyAdapter myAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mHeaderHint = findViewById(R.id.header_hint);
        setListView();
    }

    @Override
    protected void onStart() {
        super.onStart();
        getWifiInfo();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        switch (itemId) {
            case R.id.menu:
                startActivity(new Intent(MainActivity.this, AboutActivity.class));
                break;
        }
        return true;
    }

    class MyAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return mWifiInfoList == null ? 0 : mWifiInfoList.size();
        }

        @Override
        public WifiInfo getItem(int position) {
            return mWifiInfoList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @SuppressLint("InflateParams")
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                holder = new ViewHolder();
                convertView = LayoutInflater.from(MainActivity.this).inflate(R.layout.item_list, null);
                holder.name = convertView.findViewById(R.id.item_name);
                holder.password = convertView.findViewById(R.id.item_password);

                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            WifiInfo wifi = getItem(position);

            holder.name.setText(String.format("%s%s", getString(R.string.iten_name_hint), wifi.getName()));
            holder.password.setText(String.format("%s%s", getString(R.string.item_pasword_hint), wifi.getPassword()));

            return convertView;
        }

        class ViewHolder {
            TextView name;
            TextView password;
        }
    }

    private void setListView() {
        ListView mListView = findViewById(R.id.listview);
        myAdapter = new MyAdapter();
        mListView.setAdapter(myAdapter);
        mListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                ClipboardManager cmb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cmb.setPrimaryClip(ClipData.newPlainText(getString(R.string.item_pasword_hint), mWifiInfoList.get(position)
                        .getPassword()));
                Toast.makeText(getApplicationContext(), R.string.copy_success, Toast.LENGTH_SHORT).show();
                return true;
            }
        });
    }

    private void getWifiInfo() {
        Process process = null;
        DataOutputStream dataOutputStream = null;
        DataInputStream dataInputStream = null;
        StringBuilder wifiConf = new StringBuilder();
        try {
            process = Runtime.getRuntime().exec("su");
            dataOutputStream = new DataOutputStream(process.getOutputStream());
            dataInputStream = new DataInputStream(process.getInputStream());
            dataOutputStream.writeBytes("cat /data/misc/apexdata/com.android.wifi/WifiConfigStore.xml\n");
            dataOutputStream.writeBytes("exit\n");
            dataOutputStream.flush();
            InputStreamReader inputStreamReader = new InputStreamReader(dataInputStream, StandardCharsets.UTF_8);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                wifiConf.append(line);
            }
            bufferedReader.close();
            inputStreamReader.close();
            process.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        } finally {
            if (wifiConf.length() == 0) {
                mHeaderHint.setText(R.string.not_root_hint_txt);
                Toast.makeText(getApplicationContext(), R.string.not_root_hint, Toast.LENGTH_LONG).show();
            }
            try {
                if (dataOutputStream != null) {
                    dataOutputStream.close();
                }
                if (dataInputStream != null) {
                    dataInputStream.close();
                }
                if (process != null) {
                    process.destroy();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        Map<String, String> wifiConfMap = parseXml(wifiConf.toString());
        mWifiInfoList = new ArrayList<>();
        for (Map.Entry<String, String> wifiConfEntry : wifiConfMap.entrySet()) {
            WifiInfo wifiInfo = new WifiInfo();
            wifiInfo.setName(wifiConfEntry.getKey());
            wifiInfo.setPassword(wifiConfEntry.getValue());
            mWifiInfoList.add(wifiInfo);
        }
        // 列表倒序
        Collections.reverse(mWifiInfoList);
        myAdapter.notifyDataSetChanged();
    }

    // Android 11 wifi 信息存储路径和文件格式(xml)发生改变
    public Map<String,String> parseXml(String xml) {
        if (xml == null || xml.equals("")) {
            return null;
        }
        Map<String, String> result = new HashMap<>();
        try {
            Document document = DocumentHelper.parseText(xml);
            Element rootElement = document.getRootElement();
            List<Element> networkList = rootElement.element("NetworkList").elements();
            for (Element network: networkList) {
                Element wifiConfiguration = network.element("WifiConfiguration");
                List<Element> wifiConfigurationgAboutWifiInfo = wifiConfiguration.elements("string");
                Map<String, String> wifiInfoMap = new HashMap<>();
                for (Element confWifiInfo : wifiConfigurationgAboutWifiInfo) {
                    switch (confWifiInfo.attributeValue("name")) {
                        case "SSID":
                            String ssid = confWifiInfo.getText();
                            wifiInfoMap.put("SSID", ssid.substring(1, ssid.length() - 1));
                            break;
                        case "PreSharedKey":
                            String preSharedKey = confWifiInfo.getText();
                            wifiInfoMap.put("PreSharedKey", preSharedKey.substring(1, preSharedKey.length() - 1));
                            break;
                        default:
                    }
                }
                result.put(wifiInfoMap.get("SSID"), wifiInfoMap.get("PreSharedKey"));
            }
            return result;
        }catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
