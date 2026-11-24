/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.util

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Decodes lyrics files whose encoding is not declared anywhere.
 *
 * Many .lrc/.txt lyrics in the wild are still encoded with legacy code pages (GBK/GB18030,
 * Big5, Shift-JIS, EUC-KR, ...). Decoding those bytes as UTF-8 produces U+FFFD or mojibake,
 * so [decode] detects the encoding from the byte content. The user can also force a specific
 * encoding from the lyrics settings as a fallback.
 */
object LyricsEncoding {

    /** Stored value used by the manual override setting: detect the encoding from the bytes. */
    const val AUTO = "AUTO"

    enum class Option(
        val id: String,
        val charsetName: String?,
        val displayName: String
    ) {
        AUTO(LyricsEncoding.AUTO, null, "Auto"),
        UTF_8("UTF-8", "UTF-8", "UTF-8"),
        UTF_16("UTF-16", "UTF-16", "UTF-16"),
        UTF_16_LE("UTF-16LE", "UTF-16LE", "UTF-16 LE"),
        UTF_16_BE("UTF-16BE", "UTF-16BE", "UTF-16 BE"),
        GB18030("GB18030", "GB18030", "GB18030 (GBK)"),
        BIG5("Big5", "Big5", "Big5"),
        SHIFT_JIS("Shift_JIS", "Shift_JIS", "Shift-JIS"),
        EUC_KR("EUC-KR", "EUC-KR", "EUC-KR"),
        WINDOWS_1252("windows-1252", "windows-1252", "Windows-1252");

        val charset: Charset?
            get() = charsetName?.let(::charsetOrNull)

        companion object {
            fun fromId(id: String?): Option =
                entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: AUTO
        }
    }

    data class DecodeResult(
        val text: String,
        val option: Option
    )

    fun decode(bytes: ByteArray, optionId: String? = AUTO): String =
        decodeWithInfo(bytes, optionId).text

    fun decodeWithInfo(bytes: ByteArray, optionId: String? = AUTO): DecodeResult {
        if (bytes.isEmpty()) return DecodeResult("", Option.AUTO)

        // A BOM is authoritative, even when the user picked another encoding.
        bomDecode(bytes)?.let { return it }

        val requested = Option.fromId(optionId)
        requested.charset?.let { charset ->
            return DecodeResult(decodeLenient(bytes, charset), requested)
        }

        return autoDetect(bytes)
    }

    private fun autoDetect(bytes: ByteArray): DecodeResult {
        detectUtf16WithoutBom(bytes)?.let { return it }

        strictDecode(bytes, Charsets.UTF_8)?.let {
            return DecodeResult(it, Option.UTF_8)
        }

        var best = DecodeResult(decodeLenient(bytes, Charsets.UTF_8), Option.UTF_8)
        var bestScore = scoreText(best.text)

        val candidates = buildList {
            addAll(LEGACY_CANDIDATES)
            if (bytes.size % 2 == 0 && bytes.size >= 16) {
                add(Option.UTF_16_LE)
                add(Option.UTF_16_BE)
            }
        }

        for (option in candidates) {
            val charset = option.charset ?: continue
            val text = strictDecode(bytes, charset) ?: continue
            val score = scoreText(text)
            if (score > bestScore) {
                best = DecodeResult(text, option)
                bestScore = score
            }
        }

        val latinCharset = charsetOrNull("windows-1252") ?: Charsets.ISO_8859_1

        // Mostly ASCII with a few accented bytes: a single-byte Latin code page is far more
        // likely than a CJK encoding. Keep the CJK candidate when it clearly contains real
        // Chinese/Japanese/Korean text (e.g. a mostly English lyric sheet with a few GBK lines).
        if (nonAsciiRatio(bytes) < 0.30 && cjkEvidence(best.text) < 4) {
            return DecodeResult(decodeLenient(bytes, latinCharset), Option.WINDOWS_1252)
        }

        // Windows-1252 can decode any byte sequence; only prefer it when none of the CJK
        // candidates produced plausible text.
        if (bestScore < 0.25) {
            val latin = decodeLenient(bytes, latinCharset)
            val latinScore = scoreText(latin)
            if (latinScore > bestScore) {
                best = DecodeResult(latin, Option.WINDOWS_1252)
            }
        }

        return best
    }

    /**
     * Repairs embedded lyrics that were decoded with the wrong single-byte charset before being
     * stored in a tag (e.g. UTF-8 or GBK bytes read as ISO-8859-1). Only returns a repaired
     * string when it clearly contains more CJK text than the original.
     */
    fun repairMojibake(text: String): String {
        if (text.isBlank()) return text
        // Reversible mojibake only consists of Latin-1 range characters.
        if (text.any { it.code > 0xFF }) return text

        val suspicious = text.count { it.code in 0x80..0xFF }
        if (suspicious < 3 || suspicious < text.length / 20) return text

        val bytes = text.toByteArray(Charsets.ISO_8859_1)
        val decoded = decodeWithInfo(bytes)
        if (decoded.option == Option.WINDOWS_1252) return text

        return if (cjkEvidence(decoded.text) > cjkEvidence(text) + 2) decoded.text else text
    }

    private fun cjkEvidence(text: String): Int =
        text.count { ch ->
            ch in HIGH_FREQUENCY_CJK ||
                ch in '぀'..'ヿ' ||
                ch in '가'..'힯'
        }

    private fun bomDecode(bytes: ByteArray): DecodeResult? {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return DecodeResult(
                decodeLenient(bytes.copyOfRange(3, bytes.size), Charsets.UTF_8),
                Option.UTF_8
            )
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return DecodeResult(
                decodeLenient(bytes.copyOfRange(2, bytes.size), Charsets.UTF_16LE),
                Option.UTF_16_LE
            )
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return DecodeResult(
                decodeLenient(bytes.copyOfRange(2, bytes.size), Charsets.UTF_16BE),
                Option.UTF_16_BE
            )
        }
        return null
    }

    /**
     * BOM-less UTF-16 files (common on Windows) show a NUL byte in every second position for
     * ASCII-heavy content such as LRC timestamps.
     */
    private fun detectUtf16WithoutBom(bytes: ByteArray): DecodeResult? {
        if (bytes.size < 16 || bytes.size % 2 != 0) return null

        val sample = bytes.copyOfRange(0, minOf(bytes.size, 512))
        val pairs = sample.size / 2
        if (pairs < 8) return null

        var evenZeros = 0
        var oddZeros = 0
        sample.forEachIndexed { index, byte ->
            if (byte == 0.toByte()) {
                if (index % 2 == 0) evenZeros++ else oddZeros++
            }
        }

        val charset = when {
            oddZeros > pairs * 0.3 && evenZeros < pairs * 0.05 -> Charsets.UTF_16LE
            evenZeros > pairs * 0.3 && oddZeros < pairs * 0.05 -> Charsets.UTF_16BE
            else -> return null
        }
        val text = strictDecode(bytes, charset) ?: return null
        val option = if (charset == Charsets.UTF_16LE) Option.UTF_16_LE else Option.UTF_16_BE
        return DecodeResult(text, option)
    }

    private fun strictDecode(bytes: ByteArray, charset: Charset): String? =
        try {
            charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            null
        }

    private fun decodeLenient(bytes: ByteArray, charset: Charset): String =
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
            .decode(ByteBuffer.wrap(bytes))
            .toString()

    private fun nonAsciiRatio(bytes: ByteArray): Double {
        val sample = bytes.copyOfRange(0, minOf(bytes.size, 4096))
        if (sample.isEmpty()) return 0.0
        val nonAscii = sample.count { (it.toInt() and 0xFF) >= 0x80 }
        return nonAscii.toDouble() / sample.size.toDouble()
    }

    /**
     * Scores how much a decoded candidate looks like real lyrics. Correctly decoded Chinese
     * lyrics contain many high-frequency hanzi; decoding the same bytes with the wrong legacy
     * code page mostly yields rare characters, so the common-character hits separate them.
     */
    private fun scoreText(text: String): Double {
        if (text.isEmpty()) return Double.NEGATIVE_INFINITY

        var score = 0.0
        var length = 0
        for (ch in text) {
            length++
            when {
                ch == '\uFFFD' -> score -= 60.0
                ch.isISOControl() && ch != '\n' && ch != '\r' && ch != '\t' -> score -= 30.0
                ch in '\uE000'..'\uF8FF' -> score -= 30.0
                ch in '一'..'鿿' -> {
                    score += 1.0
                    if (ch in HIGH_FREQUENCY_CJK) score += 5.0
                }
                ch in '㐀'..'䶿' -> score -= 1.0
                ch in '぀'..'ヿ' -> score += 2.5 // kana
                ch in '가'..'힯' -> score += 2.5 // hangul
                ch.code in 0x20..0x7E || ch == '\n' || ch == '\r' || ch == '\t' -> score += 1.0
                ch in CJK_PUNCTUATION -> score += 2.0
                ch.code in 0xA0..0x24F -> score += 0.3 // accented Latin
                else -> score -= 0.5
            }
        }
        return score / length
    }

    private fun charsetOrNull(name: String): Charset? =
        try {
            Charset.forName(name)
        } catch (_: Exception) {
            null
        }

    private val LEGACY_CANDIDATES = listOf(
        Option.GB18030,
        Option.BIG5,
        Option.SHIFT_JIS,
        Option.EUC_KR
    )

    // Frequent simplified and traditional hanzi, used to tell a correct legacy decode from a
    // plausible-looking but wrong one.
    private const val HIGH_FREQUENCY_CJK =
        "的一是不了在人有我他这个们中来上大为和国地到以说时要就出会可也你对生能而子那得于着下自之年过发后作里用道行所然家种事成方多经么去法学如都同现当没动面起看定天分还进好小部其些主样理心她本前开但因只从想实日军者意无力它与长把机十民第公此已工使情明性知全三又关点正业外将两高间由问很最重并物手应战向头文体政美相见被利什二等产或新己制身果加西斯月话合回特代内信表化老给世位次度门任常先海通教儿原东声提立及比员解水名真论处走义各入几口认条平系气题活尔更别打女变四神总何电数安少报才结反受目太量再感建务做接必场件计管期市直德资命山金指克许统区保至队形社便空决治展马科司五基眼书非则听白却界达光放强即像难且权思王象完设式色路记南品住告类求据程北边死张该交规万取拉格望觉术领共确传师观清今切院让识候带导争运笑飞风步改收根干造言联持组每济车亲极林服快办议往元英士证近失转夫令准布始存远叫台单影具罗字爱击流备兵连调深商算质团集百需价花党华城石级整府离况亚请技际约示复病息究线似官火断精满支视消越器容照须九增研写称企八功吗包片史委乎查轻易早曾除农找装广显吧阿李标谈吃图念六引历首医局突专费号尽另周较注语仅考落青随选列武红响虽推势参希古众构房半节土投某案黑维革划敌致陈律足态护七兴派孩验责营星够章音跟志底站严巴例防族供效续施留讲型料终答紧黄绝奇察母京段依批群项故按河米围江织害斗双境客纪采举杀攻父苏密低朝友诉止细愿千值仍男钱破网热助倒育属坐帝限船脸职速刻乐否刚威毛状率甚独球般普怕弹校苦创假久错承印晚兰试股拿脑预谁益阳若哪微尼继送急血惊伤素药适波夜省初喜卫源食险待述陆习置居劳财环排福纳欢雷警获模充负云停木游龙树疑层冷洲冲射略范竟句室异激汉村哈策演简卡罪判担州静退既衣您宗积余痛检差富灵协角占配征修皮挥胜降阶审沉坚善妈刘读啊超免压银买皇养伊怀执副乱抗犯追帮宣佛岁航优怪香著田铁控税左右份穿艺背阵草脚概恶块顿敢守酒岛托央户烈洋哥索胡款靠评版宝座释景顾弟登货互付伯慢欧换闻危忙核暗姐介坏讨丽良序升监临亮露永呼味野架域沙掉括舰鱼杂误湾吉减编楚肯测败屋跑梦散温困剑渐封救贵枪缺楼县尚毫移娘朋画班智亦耳恩短掌恐遗固席松秘谢鲁遇康虑幸均销钟诗藏赶剧票损忽巨炮旧端探湖录叶春乡附吸予礼港雨呀板庭妇归睛饭额含顺输摇招婚脱补谓督毒油疗旅泽材灭逐莫笔亡鲜词圣择寻厂睡博勒烟授诺伦岸奥唐卖俄炸载洛健堂旁宫喝借君禁阴园谋宋避抓荣姑孙逃牙束跳顶玉镇雪午练迫爷篇肉嘴馆遍凡础洞卷坦牛宁纸诸训私庄祖丝翻暴森塔默握戏隐熟骨访弱蒙歌店鬼软典欲萨伙遭盘爸扩盖弄雄稳忘亿刺拥徒姆杨齐赛趣曲刀床迎冰虚玩析窗醒妻透购替塞努休虎扬途侵刑绿兄迅套贸毕唯谷轮库迹尤竞街促延震弃甲伟麻川申缓潜闪售灯针哲络抵朱埃抱鼓植纯夏忍页杰筑折郑贝尊吴秀混臣雅振染盛怒舞圆搞狂措姓残秋培迷诚宽宇猛摆梅毁伸摩盟末乃悲拍丁赵硬麦蒋操耶阻订彩抽赞魔纷沿喊违妹浪汇币丰蓝殊献桌啦瓦莱援译夺汽烧距裁偏符勇触课敬哭懂墙袭召罚侠厅拜巧侧韩冒债曼融惯享戴童犹乘挂奖绍厚纵障讯涉彻刊丈爆乌役描洗玛患妙镜唱烦签仙彼弗症仿倾牌陷鸟轰咱菜闭奋庆撤泪茶疾缘播朗杜奶季丹狗尾仪偷奔珠虫驻孔宜艾桥淡翼恨繁寒伴叹旦愈潮粮缩罢聚径恰挑袋灰捕徐珍幕映裂泰隔启尖忠累炎暂估泛荒偿横拒瑞忆孤鼻闹羊呆厉衡胞零穷舍码赫婆魂灾洪腿胆津俗辩胸晓劲贫仁偶辑邦恢赖圈摸仰润堆碰艇稍迟辆废净凶署壁御奉旋冬矿抬蛋晨伏吹鸡倍糊秦盾杯租骑乏隆诊奴摄丧污渡旗甘耐凭扎抢绪粗肩梁幻菲皆碎宙叔岩荡综爬荷悉蒂返井壮薄悄扫敏碍殖详迪矛霍允幅撒剩凯颗骂赏液番箱贴漫酸郎腰舒眉忧浮辛恋餐吓挺励辞艘键伍峰尺昨黎辈贯侦滑券崇扰宪绕趋慈乔阅汗枝拖墨胁插箭腊粉泥氏彭拔骗凤慧媒佩愤扑龄驱惜豪掩兼跃尸肃帕驶堡届欣惠册储飘桑闲惨洁踪勃宾频仇磨递邪撞拟滚奏巡颜剂绩贡疯坡瞧截燃焦殿伪柳锁逼颇昏劝呈搜勤戒驾漂饮曹朵仔柔俩孟腐幼践籍牧凉牲佳娜浓芳稿竹腹跌逻垂遵脉貌柏狱猜怜惑陶兽帐饰贷昌叙躺钢沟寄扶铺邓寿惧询汤盗肥尝匆辉奈扣廷澳嘛董迁凝慰厌脏腾幽怨鞋丢埋泉涌辖躲晋紫艰魏吾慌祝邮吐狠鉴曰械咬邻赤挤弯椅陪割揭韦悟聪雾锋梯猫祥阔誉筹丛牵鸣沈阁穆屈旨袖猎臂蛇贺柱抛鼠瑟戈牢逊迈欺吨琴衰瓶恼燕仲诱狼池疼卢仗冠粒遥吕玄尘冯抚浅敦纠钻晶岂峡苍喷耗凌敲菌赔涂粹扁亏寂煤熊恭湿循暖糖赋抑秩帽哀宿踏烂袁侯抖夹昆肝擦猪炼恒慎搬纽纹玻渔磁铜齿跨押怖漠疲叛遣兹祭醉拳弥斜档稀捷肤疫肿豆削岗晃吞宏癌肚隶履涨耀扭坛拨沃绘伐堪仆郭牺歼墓雇廉契拼惩捉覆刷劫嫌瓜歇雕闷乳串娃缴唤赢莲霸桃妥瘦搭赴岳嘉舱俊址庞耕锐缝悔邀玲惟斥宅添挖呵讼氧浩羽斤酷掠妖祸侍乙妨贪挣汪尿莉悬唇翰仓轨枚盐览傅帅庙芬屏寺胖璃愚滴疏萧姿颤丑劣柯寸扔盯辱匹俱辨饿蜂哦腔郁溃谨糟葛苗肠忌溜鸿爵鹏鹰笼丘桂滋聊挡纲肌茨壳痕碗穴膀卓贤卧膜毅锦欠哩函茫昂薛皱夸豫胃舌剥傲拾窝睁携陵哼棉晴铃填饲渴吻扮逆脆喘罩卜炉柴愉绳胎蓄眠竭喂傻慕浑奸扇柜悦拦诞饱乾泡贼亭夕爹酬儒姻卵氛泄杆挨僧蜜吟猩遂狭肖甜霞驳裕顽於摘矮秒卿畜咽披辅勾盆疆赌塑畏吵囊嗯泊肺骤缠冈羞瞪吊贾漏斑涛悠鹿俘锡卑葬铭滩嫁催璇翅盒蛮矣潘歧赐鲍锅廊拆灌勉盲宰佐啥胀扯禧辽抹筒棋裤唉朴咐孕誓喉妄拘链驰栏逝窃艳臭纤玑棵趁匠盈翁愁瞬婴孝颈倘浙谅蔽畅赠妮莎尉冻跪闯葡後厨鸭颠遮谊圳吁仑辟瘤嫂陀框谭亨钦庸歉芝吼甫衫摊宴嘱衷娇陕矩浦讶耸裸碧摧薪淋耻胶屠鹅饥盼脖虹翠崩账萍逢赚撑翔倡绵猴枯巫昭怔渊凑溪蠢禅阐旺寓藤匪伞碑挪琼脂谎慨菩萄狮掘抄岭晕逮砍掏狄晰罕挽脾舟痴蔡剪脊弓懒叉拐喃僚捐姊骚拓歪粘柄坑陌窄湘兆崖骄刹鞭芒筋聘钩棍嚷腺弦焰耍俯厘愣厦恳饶钉寡憾摔叠惹喻谱愧煌徽溶坠煞巾滥洒堵瓷" +
            "這們來為國說時會對個發後裡麼學過現當動還進兒東聽覺讓識帶爭車親極辦議證轉單愛擊連調質團價華請復線斷滿視增寫稱歷醫專費較語隨選紅響勢構節頭嚴實續講終緊絕黃齊龍樹衝範漢簡擔靜媽讀壓銀買養執亂幫歲優鐵稅藝陣腳惡頓島戶業樂屆儲飄閒慘潔頻遞擬滾顏績貢瘋偽鎖勸駕飲傑築鄭貝吳"

    private const val CJK_PUNCTUATION =
        "、。〃〈〉《》「」『』【】〔〕〖〗—‘’“”…·！（）＊＋，－．／：；＜＝＞？［］｛｜｝～　"
}
