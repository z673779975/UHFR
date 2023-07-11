package com.handheld.uhfr;

import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import com.uhf.api.cls.R2000_calibration.TagLED_DATA;
import com.uhf.api.cls.ReadListener;
import com.uhf.api.cls.Reader;
import com.uhf.api.cls.Reader.AntPower;
import com.uhf.api.cls.Reader.AntPowerConf;
import com.uhf.api.cls.Reader.HardwareDetails;
import com.uhf.api.cls.Reader.HoptableData_ST;
import com.uhf.api.cls.Reader.Inv_Potl;
import com.uhf.api.cls.Reader.Inv_Potls_ST;
import com.uhf.api.cls.Reader.Lock_Obj;
import com.uhf.api.cls.Reader.Lock_Type;
import com.uhf.api.cls.Reader.Mtr_Param;
import com.uhf.api.cls.Reader.READER_ERR;
import com.uhf.api.cls.Reader.Region_Conf;
import com.uhf.api.cls.Reader.SL_TagProtocol;
import com.uhf.api.cls.Reader.TAGINFO;
import com.uhf.api.cls.Reader.TagFilter_ST;
import com.uhf.api.cls.Reader.deviceVersion;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Objects;

import cn.pda.serialport.SerialPort;
import cn.pda.serialport.Tools;

/**
 * @author LeiHuang
 */
public class UHFRManager {
    //6106
    private static SerialPort sSerialPort;
    private final String tag = "UHFRManager";
    private static Reader reader;
    private final int[] ants = new int[]{1};
    private final int ant = 1;
    public deviceVersion dv;
    public static READER_ERR mErr = READER_ERR.MT_CMD_FAILED_ERR;
    private static final int port = 13;

    private static boolean DEBUG = false;

    private int rPower = 0;
    private int wPower = 0;

    public static void setDebuggable(boolean debuggable) {
        DEBUG = debuggable;
    }

    private static void logPrint(String content) {
        if (DEBUG) {
            Log.e("huang,UHFRManager", content);
        }
    }

    private static void logPrint(String tag, String content) {
        if (DEBUG) {
            Log.e("[" + tag + "]->", content);
        }
    }

    private static UHFRManager uhfrManager = null;

    /**
     * init Uhf module
     *
     * @return UHFRManager
     */
    public static UHFRManager getInstance() {
        long enterTime = SystemClock.elapsedRealtime();
        if (uhfrManager == null) {
            if (connect()) {
                uhfrManager = new UHFRManager();
            }
        }
        long outTime = SystemClock.elapsedRealtime();
        Log.i("zeng-", "init uhf time: " + (outTime - enterTime));
        return uhfrManager;
    }

    public boolean close() {

        if (reader != null) {
            reader.CloseReader();
        }
        reader = null;

        new SerialPort().power_5Voff();
        uhfrManager = null;
        return true;

    }

    public String getHardware() {
        String version = null;

        HardwareDetails val = reader.new HardwareDetails();
        READER_ERR er = reader.GetHardwareDetails(val);
        if (er == READER_ERR.MT_OK_ERR) {
            version = "1.1.02." + val.module.value();
        }
        return version;


    }

    private static boolean connect() {
        // 重置静态变量标志
        isE710 = false;
        OutputStream outputStream = null;
        InputStream inputStream = null;
        SerialPort serialPort = null;
        try {
            new SerialPort().power_5Von();
//            new SerialPort().scaner_poweron();
            Thread.sleep(500);
            serialPort = new SerialPort(port, 115200, 0);
            // 国芯获取版本号指令
            String cmd = "FF00031D0C";
            outputStream = serialPort.getOutputStream();
            outputStream.write(Tools.HexString2Bytes(cmd));
            outputStream.flush();
            Thread.sleep(20);
            byte[] bytes = new byte[128];
            inputStream = serialPort.getInputStream();
            int read = inputStream.read(bytes);
            String retStr = Tools.Bytes2HexString(bytes, read);
            if (retStr.length() > 40) {
                // 获取到芯联R2000模块返回
                isE710 = false;
            } else {
                // E710
                serialPort.close(port);
                serialPort = new SerialPort(port, 921600, 0);
                outputStream = serialPort.getOutputStream();
                inputStream = serialPort.getInputStream();
                cmd = "FF00031D0C";
                outputStream.write(Tools.HexString2Bytes(cmd));
                outputStream.flush();
                Thread.sleep(20);
                read = inputStream.read(bytes);
                retStr = Tools.Bytes2HexString(bytes, read);
                logPrint("zeng-", "retStr2:" + retStr);
                if (retStr.length() > 40) {
                    // 获取到芯联E710模块返回
                    isE710 = true;
                }
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        } finally {
            try {
                if (outputStream != null) {
                    outputStream.close();
                }
                if (inputStream != null) {
                    inputStream.close();
                }
                if (serialPort != null) {
                    serialPort.close(port);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        reader = new Reader();
        READER_ERR er;
        long enterTime = SystemClock.elapsedRealtime();
        if (isE710) {
            // E710 波特率921600
            er = reader.InitReader_Notype("/dev/ttyMT1:921600", 1);
        } else {
            // R2000 波特率115200
            er = reader.InitReader_Notype("/dev/ttyMT1", 1);
        }
        long outTime = SystemClock.elapsedRealtime();
        if (er == READER_ERR.MT_OK_ERR) {
            if (connect2()) {
                return true;
            }
        }
        reader.CloseReader();
        new SerialPort().power_5Voff();
        return false;
    }


    private static boolean isE710 = false;

    //芯联专属方法
    private static boolean connect2() {
        long enterTime = SystemClock.elapsedRealtime();
        Inv_Potls_ST ipst = reader.new Inv_Potls_ST();
        List<SL_TagProtocol> ltp = new ArrayList<SL_TagProtocol>();
        ltp.add(SL_TagProtocol.SL_TAG_PROTOCOL_GEN2);
        ipst.potlcnt = ltp.size();
        ipst.potls = new Inv_Potl[ipst.potlcnt];
        SL_TagProtocol[] stp = ltp.toArray(new SL_TagProtocol[ipst.potlcnt]);
        for (int i = 0; i < ipst.potlcnt; i++) {
            Inv_Potl ipl = reader.new Inv_Potl();
            ipl.weight = 30;
            ipl.potl = stp[i];
            ipst.potls[0] = ipl;
        }
        READER_ERR er = reader.ParamSet(Mtr_Param.MTR_PARAM_TAG_INVPOTL, ipst);
        long outTime = SystemClock.elapsedRealtime();
        Log.i("zeng-", "connect2 cusTime: " + (outTime - enterTime));
        return er == READER_ERR.MT_OK_ERR;
    }

    //芯联专用，设置波特率
    public static boolean setBaudrate(int baudtrate) {
        Reader.Default_Param dp = reader.new Default_Param();
        dp.isdefault = false;
        dp.key = Mtr_Param.MTR_PARAM_SAVEINMODULE_BAUD;
        dp.val = baudtrate;
//        int[] val = {baudtrate} ;
        READER_ERR er = reader.ParamSet(Mtr_Param.MTR_PARAM_SAVEINMODULE_BAUD, dp);
//        logPrint("pang ", "Error = " + er.name());
        return er == READER_ERR.MT_OK_ERR;
    }


    public READER_ERR asyncStartReading() {
        if (isE710 && !isEmb) {
            /*新E7快速模式*/
            logPrint("pang", "E710 AsyncStartReading");
            Reader.CustomParam_ST cpst = reader.new CustomParam_ST();
            cpst.ParamName = "Reader/Ex10fastmode";
            byte[] vals = new byte[22];
            vals[0] = 1;
            vals[1] = 20;
            for (int i = 0; i < 20; i++)
                vals[2 + i] = 0;
            cpst.ParamVal = vals;
            reader.ParamSet(Mtr_Param.MTR_PARAM_CUSTOM, cpst);
            return reader.AsyncStartReading(ants, 1, 0);
        } else {
            int session = getGen2session();
            logPrint("pang", "AsyncStartReading");
            int option = 16;

            if (session == 1) {
                if (isEmb) {
                    option = Emboption;
                }
                return reader.AsyncStartReading(ants, 1, option);
            } else {
                option = 0;
                if (isEmb) {
                    option = Emboption;
                }
                return reader.AsyncStartReading(ants, 1, option);
            }
        }
    }

    private List<TAGINFO> listTag = new ArrayList<>();
    //E710 E智能模式的异步接收数据
    private ReadListener readListener = new ReadListener() {
        @Override
        public void tagRead(Reader reader, TAGINFO[] taginfos) {
            synchronized (listTag) {
                if (taginfos != null && taginfos.length > 0) {
//                    listTag.clear();
                    Collections.addAll(listTag, taginfos);
                }
            }
        }
    };

    public READER_ERR asyncStartReading(int option) {


//            READER_ERR er = reader.ParamSet(Mtr_Param.MTR_PARAM_TAG_EMBEDEDDATA, null);
//            logPrint("asyncStartReading, ParamSet MTR_PARAM_TAG_EMBEDEDDATA result: " + er.toString());
        // 设置gen2 tag编码(PROF)
//        int[] val = new int[] {19};//profile3，默认M4
//        er = reader.ParamSet(Mtr_Param.MTR_PARAM_POTL_GEN2_TAGENCODING, val);
//        logPrint("asyncStartReading, ParamSet MTR_PARAM_POTL_GEN2_TAGENCODING result: " + er.toString());
        if (isE710) {
            /*新E7快速模式，有待验证                 */
            logPrint("pang", "AsyncStartReading");
            Reader.CustomParam_ST cpst = reader.new CustomParam_ST();
            cpst.ParamName = "Reader/Ex10fastmode";
            byte[] vals = new byte[22];
            vals[0] = 1;
            vals[1] = 20;
            for (int i = 0; i < 20; i++)
                vals[2 + i] = 0;
            cpst.ParamVal = vals;
            reader.ParamSet(Mtr_Param.MTR_PARAM_CUSTOM, cpst);

            return reader.AsyncStartReading(ants, 1, 0);
        }
        return reader.AsyncStartReading(ants, 1, option);
    }

    public READER_ERR asyncStopReading() {


        if (isE710) {
            READER_ERR er = reader.AsyncStopReading();
            logPrint("pang", "asyncStopReading");
            ///////////////E7智能模式////////
//                READER_ERR er = reader.AsyncStopReading_IT();
//                reader.removeReadListener(readListener);
//                logPrint("pang", "AsyncStopReading_IT er = " + er.name());
            ///////////////////////////////
            return er;
        } else {
            return reader.AsyncStopReading();
        }

    }

    public READER_ERR InventoryFilters() {

        int session = getGen2session();
        logPrint("pang", "AsyncStartReading");
        int option = 16;

        if (session == 1) {
            if (isEmb) {
                option = Emboption;
            }
            return reader.AsyncStartReading(ants, 1, option);
        } else {
            option = 0;
            if (isEmb) {
                option = Emboption;
            }
            return reader.AsyncStartReading(ants, 1, option);
        }

    }

    public boolean setInventoryFilters(String[] mepc) {

        READER_ERR er = reader.ParamSet(Reader.Mtr_Param.MTR_PARAM_TAG_MULTISELECTORS, mepc);
        if (er != READER_ERR.MT_OK_ERR) {
            logPrint("setInventoryFilters, ParamSet MTR_PARAM_TAG_FILTER result: " + er.toString());
            return false;
        }
        return true;

    }

    public boolean setInventoryFilter(byte[] fdata, int fbank, int fstartaddr, boolean matching) {
        TagFilter_ST g2tf;
        g2tf = reader.new TagFilter_ST();
        g2tf.fdata = fdata;
        g2tf.flen = fdata.length * 8;
        if (matching) {
            g2tf.isInvert = 0;
        } else {
            g2tf.isInvert = 1;
        }
        g2tf.bank = fbank;
        g2tf.startaddr = fstartaddr * 16;
        READER_ERR er = reader.ParamSet(Mtr_Param.MTR_PARAM_TAG_FILTER, g2tf);
        if (er != READER_ERR.MT_OK_ERR) {
            logPrint("setInventoryFilter, ParamSet MTR_PARAM_TAG_FILTER result: " + er.toString());
            return false;
        }
        return true;
    }

    //
    public boolean setCancleInventoryFilter() {


        READER_ERR er = reader.ParamSet(Mtr_Param.MTR_PARAM_TAG_FILTER, null);
        if (er != READER_ERR.MT_OK_ERR) {
            logPrint("setCancleInventoryFilter, ParamSet MTR_PARAM_TAG_FILTER result: " + er.toString());
            return false;
        }
        return true;
    }


    public List<TAGINFO> tagInventoryRealTime() {
        List<TAGINFO> list = new ArrayList<>();

        READER_ERR er;

        int[] tagcnt = new int[1];
        er = reader.AsyncGetTagCount(tagcnt);

        if (er != READER_ERR.MT_OK_ERR) {
            mErr = er;
            return null;
        }
        for (int i = 0; i < tagcnt[0]; i++) {
            TAGINFO tfs = reader.new TAGINFO();
            er = reader.AsyncGetNextTag(tfs);

            if (er == READER_ERR.MT_OK_ERR) {
                list.add(tfs);
            }
        }
        return list;
    }


    public boolean stopTagInventory() {

        READER_ERR er = reader.AsyncStopReading();
        if (er != READER_ERR.MT_OK_ERR) {
            logPrint("stopTagInventory, AsyncStopReading result: " + er.toString());
            return false;
        }
        return true;

    }

    //
    public List<TAGINFO> tagInventoryByTimer(short readtime) {

        READER_ERR er;
        List<TAGINFO> list = new ArrayList<>();

        int[] tagcnt = new int[1];
        er = reader.TagInventory_Raw(ants, 1, readtime, tagcnt);
        logPrint("tagInventoryByTimer, TagInventory_Raw er: " + er.toString() + "; tagcnt[0]=" + tagcnt[0]);

        if (er == READER_ERR.MT_OK_ERR) {
            for (int i = 0; i < tagcnt[0]; i++) {
                TAGINFO tfs = reader.new TAGINFO();
                er = reader.GetNextTag(tfs);
                if (er == READER_ERR.MT_OK_ERR) {
                    list.add(tfs);
                } else {
                    //GetNextTag出现异常的时候跳出
                    break;
                }
            }

        } else {
            mErr = er;
            list = null;
//                return null;
        }

        return list;

    }


    /**
     * 盘存时附加数据读取tid（非FastTid）
     */
    public List<TAGINFO> tagEpcTidInventoryByTimer(short readtime) {


        List<TAGINFO> list = new ArrayList<>();
        READER_ERR er;

        Reader.EmbededData_ST edst = reader.new EmbededData_ST();
        edst.accesspwd = null;
        edst.bank = 2;
        edst.startaddr = 0;
        edst.bytecnt = 12;
        reader.ParamSet(Mtr_Param.MTR_PARAM_TAG_EMBEDEDDATA, edst);

        int[] tagcnt = new int[1];
        er = reader.TagInventory_Raw(ants, 1, readtime, tagcnt);
        if (er != READER_ERR.MT_OK_ERR) {
            mErr = er;
            return null;
        }

        for (int i = 0; i < tagcnt[0]; i++) {
            TAGINFO tfs = reader.new TAGINFO();
            er = reader.GetNextTag(tfs);
            if (er == READER_ERR.MT_OK_ERR) {
                list.add(tfs);
            } else {
                break;
            }
        }
        return list;

    }

    public List<TAGINFO> tagEpcOtherInventoryByTimer(short readtime, int bank, int startaddr, int bytecnt, @NonNull byte[] accesspwd) {

        List<TAGINFO> list = new ArrayList<>();
        READER_ERR er;

        //by lbx 2017-4-27 get other:res=0, bank epc =1 tid=2 user =3
        Reader.EmbededData_ST edst = reader.new EmbededData_ST();
        edst.bank = bank;
        edst.startaddr = startaddr;
        edst.bytecnt = bytecnt;
        edst.accesspwd = accesspwd;
        reader.ParamSet(Mtr_Param.MTR_PARAM_TAG_EMBEDEDDATA, edst);

        int[] tagcnt = new int[1];
        er = reader.TagInventory_Raw(ants, 1, readtime, tagcnt);
        if (er != READER_ERR.MT_OK_ERR) {
            mErr = er;
            return null;
        }

        for (int i = 0; i < tagcnt[0]; i++) {
            TAGINFO tfs = reader.new TAGINFO();
            er = reader.GetNextTag(tfs);
            if (er == READER_ERR.MT_OK_ERR) {
                list.add(tfs);
            } else {
                break;
            }
        }
        return list;
    }

    int Emboption = 0;//附加数据操作
    private boolean isEmb = false;//是否附加数据返回

    //设置附加数据返回
    public boolean setEMBEDEDATA(int bank, int startaddr, int bytecnt, byte[] accesspwd) {
        boolean flag = false;
        READER_ERR er;
        isEmb = true;
        Emboption = 0x0080;
        Emboption = (Emboption << 8);
        //by lbx 2017-4-27 get other:res=0, bank epc =1 tid=2 user =3
        Reader.EmbededData_ST edst = reader.new EmbededData_ST();
        edst.bank = bank;
        edst.startaddr = startaddr;
        edst.bytecnt = bytecnt;
        edst.accesspwd = accesspwd;
        er = reader.ParamSet(Mtr_Param.MTR_PARAM_TAG_EMBEDEDDATA, edst);
        if (er == READER_ERR.MT_OK_ERR) {
            flag = true;
        }
        return flag;
    }

    //取消附加数据
    public boolean cancelEMBEDEDATA() {
        boolean flag = false;

        READER_ERR er;
        er = reader.ParamSet(Mtr_Param.MTR_PARAM_TAG_EMBEDEDDATA, null);
        if (er == READER_ERR.MT_OK_ERR) {
            isEmb = false;
            flag = true;
        }

        return flag;
    }

    public READER_ERR getTagData(int mbank, int startaddr, int len,
                                 @NonNull byte[] rdata, byte[] password, short timeout) {
        READER_ERR er = reader.ParamSet(Mtr_Param.MTR_PARAM_TAG_FILTER, null);
        if (er == READER_ERR.MT_OK_ERR) {
            int trycount = 3;
            do {
                er = reader.GetTagData(ant, (char) mbank, startaddr, len,
                        rdata, password, timeout);

                trycount--;
                if (trycount < 1) {
                    break;
                }
            } while (er != READER_ERR.MT_OK_ERR);

            if (er != READER_ERR.MT_OK_ERR) {
                logPrint("getTagData, GetTagData result: " + er.toString());
            }
        } else {
            logPrint("getTagData, ParamSet MTR_PARAM_TAG_FILTER result: " + er.toString());
        }
        return er;

    }

    public byte[] getTagDataByFilter(int mbank, int startaddr, int len,
                                     byte[] password, short timeout, byte[] fdata, int fbank,
                                     int fstartaddr, boolean matching) {

        TagFilter_ST g2tf;
        g2tf = reader.new TagFilter_ST();
        g2tf.fdata = fdata;
        g2tf.flen = fdata.length * 8;
        if (matching) {
            g2tf.isInvert = 0;
        } else {
            g2tf.isInvert = 1;
        }
        g2tf.bank = fbank;
        g2tf.startaddr = fstartaddr * 16;
        byte[] rdata = new byte[len * 2];
        READER_ERR er = reader.ParamSet(Mtr_Param.MTR_PARAM_TAG_FILTER, g2tf);
        if (er == READER_ERR.MT_OK_ERR) {
            er = reader.GetTagData(ant, (char) mbank, startaddr, len,
                    rdata, password, (short) timeout);
            if (er == READER_ERR.MT_OK_ERR) {
                return rdata;
            } else {
                logPrint("getTagDataByFilter, GetTagData result: " + er.toString());
                return null;
            }
        } else {
            logPrint("getTagDataByFilter, ParamSet MTR_PARAM_TAG_FILTER result: " + er.toString());
            return null;
        }

    }

    public READER_ERR writeTagData(char mbank, int startaddress, byte[] data,
                                   int datalen, byte[] accesspasswd, short timeout) {

        READER_ERR er = reader.ParamSet(Mtr_Param.MTR_PARAM_TAG_FILTER, null);
        if (er == READER_ERR.MT_OK_ERR) {
            int trycount = 3;
            do {
                er = reader.WriteTagData(1, mbank, startaddress, data, datalen,
                        accesspasswd, timeout);
                trycount--;
                if (trycount < 1) {
                    break;
                }
            } while (er != READER_ERR.MT_OK_ERR);

            if (er != READER_ERR.MT_OK_ERR) {
                logPrint("writeTagData, WriteTagData result: " + er.toString());
            }
        } else {
            logPrint("writeTagData, ParamSet MTR_PARAM_TAG_FILTER result: " + er.toString());
        }
        return er;

    }

    public READER_ERR writeTagDataByFilter(char mbank, int startaddress,
                                           byte[] data, int datalen, byte[] accesspasswd, short timeout,
                                           byte[] fdata, int fbank, int fstartaddr, boolean matching) {


        TagFilter_ST g2tf;
        g2tf = reader.new TagFilter_ST();
        g2tf.fdata = fdata;
        g2tf.flen = fdata.length * 8;
        if (matching) {
            g2tf.isInvert = 0;
        } else {
            g2tf.isInvert = 1;
        }
        g2tf.bank = fbank;
        g2tf.startaddr = fstartaddr * 16;

        READER_ERR er = reader.ParamSet(Mtr_Param.MTR_PARAM_TAG_FILTER, g2tf);
        if (er == READER_ERR.MT_OK_ERR) {
            int trycount = 3;
            do {
                er = reader.WriteTagData(1, mbank, startaddress, data, datalen,
                        accesspasswd, timeout);
                trycount--;
                if (trycount < 1) {
                    break;
                }
            } while (er != READER_ERR.MT_OK_ERR);

            if (er != READER_ERR.MT_OK_ERR) {
                logPrint("writeTagDataByFilter, WriteTagData result: " + er.toString());
            }
        } else {
            logPrint("writeTagDataByFilter, ParamSet MTR_PARAM_TAG_FILTER result: " + er.toString());
        }
        return er;
    }

    public READER_ERR writeTagEPC(byte[] data, byte[] accesspwd, short timeout) {
        READER_ERR er = reader.ParamSet(Mtr_Param.MTR_PARAM_TAG_FILTER, null);
        int trycount = 3;
        do {
            er = reader.WriteTagEpcEx(ant, data, data.length, accesspwd,
                    timeout);
            if (trycount < 1) {
                break;
            }
            trycount--;
        } while (er != READER_ERR.MT_OK_ERR);

        if (er != READER_ERR.MT_OK_ERR) {
            logPrint("writeTagEPC, WriteTagEpcEx result: " + er.toString());
        }
        return er;

    }

    public READER_ERR writeTagEPCByFilter(byte[] data, byte[] accesspwd,
                                          short timeout, byte[] fdata, int fbank, int fstartaddr,
                                          boolean matching) {
        TagFilter_ST g2tf;
        g2tf = reader.new TagFilter_ST();
        g2tf.fdata = fdata;
        g2tf.flen = fdata.length * 8;
        if (matching) {
            g2tf.isInvert = 0;
        } else {
            g2tf.isInvert = 1;
        }

        g2tf.bank = fbank;
        g2tf.startaddr = fstartaddr * 16;

        READER_ERR er = reader.ParamSet(Mtr_Param.MTR_PARAM_TAG_FILTER, g2tf);
        if (er == READER_ERR.MT_OK_ERR) {
            int trycount = 3;
            do {
                er = reader.WriteTagEpcEx(ant, data, data.length, accesspwd,
                        timeout);
                if (trycount < 1) {
                    break;
                }
                trycount = trycount - 1;
            } while (er != READER_ERR.MT_OK_ERR);
            if (er != READER_ERR.MT_OK_ERR) {
                logPrint("writeTagEPCByFilter, WriteTagEpcEx result: " + er.toString());
            }
        } else {
            logPrint("writeTagEPCByFilter, ParamSet MTR_PARAM_TAG_FILTER result: " + er.toString());
        }
        return er;

    }

    public READER_ERR lockTag(Lock_Obj lockobject, Lock_Type locktype,
                              byte[] accesspasswd, short timeout) {
        READER_ERR er = reader.ParamSet(Mtr_Param.MTR_PARAM_TAG_FILTER, null);
        if (er == READER_ERR.MT_OK_ERR) {
            er = reader.LockTag(ant, (byte) lockobject.value(),
                    (short) locktype.value(), accesspasswd, timeout);
        }
        if (er != READER_ERR.MT_OK_ERR) {
            logPrint("lockTag, ParamSet MTR_PARAM_TAG_FILTER result: " + er.toString());
        }
        return er;
    }

    public READER_ERR lockTagByFilter(Lock_Obj lockobject, Lock_Type locktype,
                                      byte[] accesspasswd, short timeout, byte[] fdata, int fbank,
                                      int fstartaddr, boolean matching) {
        TagFilter_ST g2tf;
        g2tf = reader.new TagFilter_ST();
        g2tf.fdata = fdata;
        g2tf.flen = fdata.length * 8;
        if (matching) {
            g2tf.isInvert = 0;
        } else {
            g2tf.isInvert = 1;
        }
        g2tf.bank = fbank;
        g2tf.startaddr = fstartaddr * 16;

        READER_ERR er = reader.ParamSet(Mtr_Param.MTR_PARAM_TAG_FILTER, g2tf);
        if (er == READER_ERR.MT_OK_ERR) {
            er = reader.LockTag(ant, (byte) lockobject.value(),
                    (short) locktype.value(), accesspasswd, timeout);
        }
        if (er != READER_ERR.MT_OK_ERR) {
            logPrint("lockTagByFilter, ParamSet MTR_PARAM_TAG_FILTER result: " + er.toString());
        }
        return er;

    }

    public READER_ERR killTag(byte[] killpasswd, short timeout) {

        READER_ERR er = reader.ParamSet(Mtr_Param.MTR_PARAM_TAG_FILTER, null);
        if (er == READER_ERR.MT_OK_ERR) {
            er = reader.KillTag(ant, killpasswd, timeout);
        }
        if (er != READER_ERR.MT_OK_ERR) {
            logPrint("killTag, ParamSet MTR_PARAM_TAG_FILTER result: " + er.toString());
        }
        return er;
    }

    public READER_ERR killTagByFilter(byte[] killpasswd, short timeout,
                                      byte[] fdata, int fbank, int fstartaddr, boolean matching) {

        TagFilter_ST g2tf;
        g2tf = reader.new TagFilter_ST();
        g2tf.fdata = fdata;
        g2tf.flen = fdata.length * 8;
        if (matching) {
            g2tf.isInvert = 0;
        } else {
            g2tf.isInvert = 1;
        }
        g2tf.bank = fbank;
        g2tf.startaddr = fstartaddr * 16;

        READER_ERR er = reader.ParamSet(Mtr_Param.MTR_PARAM_TAG_FILTER, g2tf);
        if (er == READER_ERR.MT_OK_ERR) {
            er = reader.KillTag(ant, killpasswd, timeout);
        }
        if (er != READER_ERR.MT_OK_ERR) {
            logPrint("killTagByFilter, ParamSet MTR_PARAM_TAG_FILTER result: " + er.toString());
        }
        return er;

    }

    public READER_ERR setRegion(Region_Conf region) {

        return reader.ParamSet(Mtr_Param.MTR_PARAM_FREQUENCY_REGION, region);

    }

    public Region_Conf getRegion() {
        Region_Conf[] rcf2 = new Region_Conf[1];
        READER_ERR er = reader.ParamGet(Mtr_Param.MTR_PARAM_FREQUENCY_REGION,
                rcf2);
        if (er == READER_ERR.MT_OK_ERR) {
            return rcf2[0];
        }
        logPrint("getRegion, ParamGet MTR_PARAM_FREQUENCY_REGION result: " + er.toString());
        return null;
    }


    //获取频点，已完成，待测试
    public int[] getFrequencyPoints() {

        HoptableData_ST hdst2 = reader.new HoptableData_ST();
        READER_ERR er = reader.ParamGet(Mtr_Param.MTR_PARAM_FREQUENCY_HOPTABLE,
                hdst2);
        int[] tablefre;
        if (er == READER_ERR.MT_OK_ERR) {
            tablefre = sort(hdst2.htb, hdst2.lenhtb);
            return tablefre;
        }
        logPrint("getFrequencyPoints, ParamGet MTR_PARAM_FREQUENCY_HOPTABLE result: " + er.toString());
        return null;
    }

    //设置频点，已完成，待测试
    public READER_ERR setFrequencyPoints(int[] frequencyPoints) {

        HoptableData_ST hdst = reader.new HoptableData_ST();
        hdst.lenhtb = frequencyPoints.length;
        hdst.htb = frequencyPoints;
        return reader.ParamSet(Mtr_Param.MTR_PARAM_FREQUENCY_HOPTABLE, hdst);
    }


    //设置功率
    public READER_ERR setPower(int readPower, int writePower) {
        AntPowerConf antPowerConf = reader.new AntPowerConf();
        antPowerConf.antcnt = ant;
        AntPower antPower = reader.new AntPower();
        antPower.antid = 1;
        antPower.readPower = (short) ((short) readPower * 100);
        antPower.writePower = (short) ((short) writePower * 100);
        antPowerConf.Powers[0] = antPower;
        return reader.ParamSet(Mtr_Param.MTR_PARAM_RF_ANTPOWER, antPowerConf);

    }

    //获取功率
    public int[] getPower() {

        int[] powers = new int[2];
        AntPowerConf apcf2 = reader.new AntPowerConf();
        READER_ERR er = reader.ParamGet(
                Mtr_Param.MTR_PARAM_RF_ANTPOWER, apcf2);
        if (er == READER_ERR.MT_OK_ERR) {
            powers[0] = apcf2.Powers[0].readPower / 100;
            powers[1] = apcf2.Powers[0].writePower / 100;
            return powers;
        } else {
            logPrint("getPower, ParamGet MTR_PARAM_RF_ANTPOWER result: " + er.toString());
            return null;
        }

    }

    //悦和标签
//    public List<com.handheld.uhfr.Reader.TEMPTAGINFO> getYueheTagTemperature(byte[] accesspassword) {
//        List<com.handheld.uhfr.Reader.TEMPTAGINFO> taginfos = null;
//
//        return taginfos;
//    }

    private int[] sort(int[] array, int len) {
        int tmpIntValue;
        for (int xIndex = 0; xIndex < len; xIndex++) {
            for (int yIndex = 0; yIndex < len; yIndex++) {
                if (array[xIndex] < array[yIndex]) {
                    tmpIntValue = array[xIndex];
                    array[xIndex] = array[yIndex];
                    array[yIndex] = tmpIntValue;
                }
            }
        }
        return array;
    }

    public boolean setGen2session(boolean isMulti) {
        try {
            READER_ERR er;
            int[] val = new int[]{-1};
            if (isMulti) {
                val[0] = 1;
                if (isE710) {
                    val[0] = 2;
                    //E710模式无需设置
                    return true;
                }
            } else {
                // session0
                val[0] = 0;
            }
            er = reader.ParamSet(Mtr_Param.MTR_PARAM_POTL_GEN2_SESSION, val);

            //////////清除快速模式设置////////////
            Reader.CustomParam_ST cpst = reader.new CustomParam_ST();
            cpst.ParamName = "Reader/Ex10fastmode";
            byte[] vals = new byte[22];
            vals[0] = 0;
            vals[1] = 20;
            for (int i = 0; i < 20; i++)
                vals[2 + i] = 0;
            cpst.ParamVal = vals;
            reader.ParamSet(Mtr_Param.MTR_PARAM_CUSTOM, cpst);
            ///////////////////////////////////
            return er == READER_ERR.MT_OK_ERR;
        } catch (Exception var4) {
            return false;
        }

    }

    public boolean setGen2session(int session) {

        try {
            int[] val = new int[]{-1};
            val[0] = session;
            READER_ERR er = reader.ParamSet(Mtr_Param.MTR_PARAM_POTL_GEN2_SESSION, val);
            return er == READER_ERR.MT_OK_ERR;
        } catch (Exception var4) {
            return false;
        }

    }

    /**
     * 获取session
     *
     * @return
     */
    public int getGen2session() {
        int[] val = new int[]{-1};
        READER_ERR er = reader.ParamGet(Mtr_Param.MTR_PARAM_POTL_GEN2_SESSION, val);
        if (er == READER_ERR.MT_OK_ERR) {
            logPrint("pang", "getGen2session = " + val[0]);
            return val[0];
        }
        return -1;
    }

    private int Q = 0;

    // 设置Q值
    public boolean setQvaule(int qvaule) {
        boolean flag = false;
//            int[] val = new int[]{-1};
//            val[0] = qvaule;
//            READER_ERR er = reader.ParamSet(Mtr_Param.MTR_PARAM_POTL_GEN2_Q, val);
//            if (er == READER_ERR.MT_OK_ERR) {
//                flag = true;
//            }
        Q = qvaule;
        flag = true;


        return flag;
    }

    // 获取Q值
    public int getQvalue() {
        int value = -1;

//            int[] val = new int[]{-1};
//            READER_ERR er = reader.ParamGet(Mtr_Param.MTR_PARAM_POTL_GEN2_Q, val);
//            if (er == READER_ERR.MT_OK_ERR) {
//                value = val[0];
//            }
        value = Q;

        return value;
    }

    //获取A|B面
    public int getTarget() {
        int target = -1;

        int[] val = new int[]{-1};
        READER_ERR er = reader.ParamGet(Mtr_Param.MTR_PARAM_POTL_GEN2_TARGET, val);
        if (er == READER_ERR.MT_OK_ERR) {
            target = val[0];
        }

        return target;
    }

    //设置A|B面
    public boolean setTarget(int target) {
        boolean flag = false;

        int[] val = new int[]{-1};
        val[0] = target;
        READER_ERR er = reader.ParamSet(Mtr_Param.MTR_PARAM_POTL_GEN2_TARGET, val);
        if (er == READER_ERR.MT_OK_ERR) {
            flag = true;
        }

        return flag;
    }

    public String getInfo() {
        HardwareDetails val = reader.new HardwareDetails();
        dv = new deviceVersion();
        Reader.GetDeviceVersion("/dev/ttyMT1", dv);
        if (reader.GetHardwareDetails(val) == READER_ERR.MT_OK_ERR) {
            return "module:" + val.module.toString() + "\r\nhard:" +
                    dv.hardwareVer + "\r\nsoft:" + dv.softwareVer;
        }
        return "";
    }

    //
    public READER_ERR ReadTagLED(int ant, short timeout, short metaflag, TagLED_DATA tagled) {

        return reader.ReadTagLED(ant, timeout, metaflag, tagled);
    }

    /**
     * 开启/关闭FastTid
     */
    public boolean setFastID(boolean isOpenFastTiD) {

        if (isOpenFastTiD) {
            Reader.CustomParam_ST cpara = reader.new CustomParam_ST();
            cpara.ParamName = "tagcustomcmd/fastid";
            cpara.ParamVal = new byte[1];
            cpara.ParamVal[0] = 1;
            READER_ERR ret = reader.ParamSet(Mtr_Param.MTR_PARAM_CUSTOM, cpara);
            return ret == READER_ERR.MT_OK_ERR;
        } else {
            Reader.CustomParam_ST cpara = reader.new CustomParam_ST();
            cpara.ParamName = "tagcustomcmd/fastid";
            cpara.ParamVal = new byte[1];
            READER_ERR ret = reader.ParamSet(Mtr_Param.MTR_PARAM_CUSTOM, cpara);
            return ret == READER_ERR.MT_OK_ERR;
        }
    }

    public READER_ERR setSpecParamsForReader(Region_Conf rre) {
        Reader.SpecObject sval = reader.new SpecObject(rre);
        reader.SpecParamsForReader(0, true, sval);

        Reader.SpecObject val = reader.new SpecObject();
        reader.SpecParamsForReader(0, false, val);
        if (((Region_Conf) val.Val()) == rre) {
            sval.Val();
            return READER_ERR.MT_OK_ERR;
        } else {
            return READER_ERR.MT_CMD_FAILED_ERR;
        }

    }

    public Region_Conf getSpecParamsForReader() {
        Reader.SpecObject val = reader.new SpecObject();
        reader.SpecParamsForReader(0, false, val);
        return (Region_Conf) val.Val();
    }

}
