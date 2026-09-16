package com.niyammitra.payfixationcalculator

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class FifthCpcEventType { PROMOTION, ACP }
enum class FifthCpcFixationOption { FROM_EVENT_DATE, FROM_DNI }

data class FifthCpcEventResult(val eventDate: Long,val eventType: FifthCpcEventType,val fixationOption: FifthCpcFixationOption,val oldPay: Int,val firstIncrementedPay: Int,val secondIncrementedPay: Int? = null,val newPay: Int,val targetScale: FifthCpcScale,val nextIncrementDate: Long,val ruleBasis: List<String>)

fun calculateFifthCpcEvent(eventDate: Long,currentPay: Int,currentScale: FifthCpcScale,targetScale: FifthCpcScale,fixationOption: FifthCpcFixationOption,eventType: FifthCpcEventType = FifthCpcEventType.PROMOTION,knownDniDate: Long? = null): FifthCpcEventResult {
    require(currentPay > 0)
    require(eventDate >= fifthCpcConversionDate() && eventDate <= fifthCpcEndDate())
    require(targetScale.title != currentScale.title)
    val first = calculateFifthCpcNextStage(currentPay,currentScale) ?: currentPay
    val base = if (fixationOption == FifthCpcFixationOption.FROM_DNI) calculateFifthCpcNextStage(first,currentScale) ?: first else first
    val newPay = findEqualOrNextHigherFifthCpcStage(base,targetScale)
    val effectiveDni = if (fixationOption == FifthCpcFixationOption.FROM_DNI) {
        require(knownDniDate != null)
        nextFifthCpcDniOnOrAfter(eventDate,knownDniDate)
    } else eventDate
    // For event-date fixation, the succeeding DNI is the first day of the event month in the following year.
    val nextIncrementDate = if (fixationOption == FifthCpcFixationOption.FROM_EVENT_DATE) firstDayOfFollowingYearSameMonth(eventDate) else addFifthCpcYear(effectiveDni)
    return FifthCpcEventResult(eventDate,eventType,fixationOption,currentPay,first,if (fixationOption == FifthCpcFixationOption.FROM_DNI) base else null,newPay,targetScale,nextIncrementDate,listOf("5th CPC event fixation","Next DNI follows the applicable 5th CPC increment date; event-date fixation uses the first day of the event month in the following year."))
}

fun calculateFifthCpcNextStage(currentPay:Int,scale:FifthCpcScale):Int? = fifthCpcStages(scale.title).firstOrNull { it > currentPay }
fun findEqualOrNextHigherFifthCpcStage(currentPay:Int,scale:FifthCpcScale):Int { val s=fifthCpcStages(scale.title); if(s.isEmpty()) return maxOf(scale.payBandMinimum,currentPay); return s.firstOrNull { it>=currentPay } ?: s.last() }
private fun fifthCpcStages(n:String):List<Int>{ val a=Regex("\\d+").findAll(n.substringBefore(" (PB-" )).map{it.value.toInt()}.toList(); if(a.isEmpty()) return emptyList(); if(a.size==1)return a; val r=mutableListOf<Int>(); var c=a[0]; r+=c; var i=1; while(i+1<a.size){val inc=a[i];val end=a[i+1];if(inc<=0||end<c)break;while(c+inc<=end){c+=inc;r+=c};if(c<end){c=end;r+=c};i+=2};return r.distinct() }
private fun nextFifthCpcDniOnOrAfter(eventDate:Long,known:Long):Long{var d=known;while(d<eventDate)d=addFifthCpcYear(d);return d}
private fun addFifthCpcYear(d:Long):Long=Calendar.getInstance().apply{timeInMillis=d;add(Calendar.YEAR,1)}.timeInMillis
private fun firstDayOfFollowingYearSameMonth(d:Long):Long=Calendar.getInstance().apply{timeInMillis=d;add(Calendar.YEAR,1);set(Calendar.DAY_OF_MONTH,1)}.timeInMillis
private fun fifthCpcConversionDate():Long=Calendar.getInstance().apply{clear();set(1996,Calendar.JANUARY,1)}.timeInMillis
private fun fifthCpcEndDate():Long=Calendar.getInstance().apply{clear();set(2005,Calendar.DECEMBER,31)}.timeInMillis
private fun fifthCpcStart2005():Long=Calendar.getInstance().apply{clear();set(2005,Calendar.JANUARY,1)}.timeInMillis

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FourthToFifthEventSection(currentPay:Int,currentScale:FifthCpcScale,currentDate:Long,knownDniDate:Long?=null,onLatestStateChange:((FifthCpcScale,Int,Long)->Unit)?=null,onContinueToSixth:((FifthCpcScale,Int)->Unit)?=null){
    var events by remember(currentPay,currentScale.title,currentDate){mutableStateOf<List<FifthCpcEventResult>>(emptyList())}
    var increments by remember(currentPay,currentScale.title,currentDate){mutableStateOf<List<Pair<Int,Long>>>(emptyList())}
    var showForm by remember{mutableStateOf(true)}; var eventDate by remember{mutableStateOf<Long?>(null)}; var eventType by remember{mutableStateOf(FifthCpcEventType.PROMOTION)}; var option by remember{mutableStateOf(FifthCpcFixationOption.FROM_EVENT_DATE)}; var target by remember{mutableStateOf<FifthCpcScale?>(null)}; var menu by remember{mutableStateOf(false)}; var picker by remember{mutableStateOf(false)}
    val last=events.lastOrNull(); val pay=increments.lastOrNull()?.first ?: last?.newPay ?: currentPay; val date=increments.lastOrNull()?.second ?: last?.eventDate ?: currentDate; val scale=last?.targetScale ?: currentScale
    val ready=date>=fifthCpcStart2005() || (last?.nextIncrementDate ?: 0L)>=Calendar.getInstance().apply{clear();set(2006,Calendar.JANUARY,1)}.timeInMillis
    LaunchedEffect(scale.title,pay,date){onLatestStateChange?.invoke(scale,pay,date)}
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("5th CPC Events",fontSize=19.sp,fontWeight=FontWeight.ExtraBold,color=Color(0xFF172B4D))
        events.forEachIndexed{idx,e->Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(Color.White),shape=RoundedCornerShape(16.dp)){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("Event ${idx+1}: ${if(e.eventType==FifthCpcEventType.PROMOTION)"Promotion" else "ACP"}",Modifier.weight(1f),fontWeight=FontWeight.Bold,color=Color(0xFF1769AA));TextButton(onClick={events=events.take(idx);increments=emptyList()}){Text("Delete")}};Text("Date: ${fmt(e.eventDate)}");Text("Fixation: ${if(e.fixationOption==FifthCpcFixationOption.FROM_EVENT_DATE)"From Date of Event" else "From Date of DNI"}");Text("Fixed Pay: ${money(e.newPay)}",fontWeight=FontWeight.Bold);Text("Next DNI: ${fmt(e.nextIncrementDate)}",fontWeight=FontWeight.Bold)}}}
        if(last!=null){val nextPay=calculateFifthCpcNextStage(pay,scale);val nextDate=if(increments.isEmpty())last.nextIncrementDate else addFifthCpcYear(increments.last().second);Button(onClick={if(nextPay!=null&&nextDate<=fifthCpcEndDate())increments=increments+(nextPay to nextDate)},enabled=nextPay!=null&&nextDate<=fifthCpcEndDate(),modifier=Modifier.fillMaxWidth()){Text("Next Increment")}}
        if(!showForm&&events.isNotEmpty())Button(onClick={showForm=true;eventDate=null;target=null}){Text("Add Another Event")}
        if(showForm)Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(Color.White),shape=RoundedCornerShape(16.dp)){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("New 5th CPC Event",fontWeight=FontWeight.Bold,color=Color(0xFF1769AA));OutlinedButton(onClick={picker=true},modifier=Modifier.fillMaxWidth()){Text(eventDate?.let(::fmt)?:"Select Event Date",Modifier.weight(1f))};Row(verticalAlignment=Alignment.CenterVertically){RadioButton(eventType==FifthCpcEventType.PROMOTION,{eventType=FifthCpcEventType.PROMOTION});Text("Promotion");RadioButton(eventType==FifthCpcEventType.ACP,{eventType=FifthCpcEventType.ACP});Text("ACP")};Row(verticalAlignment=Alignment.CenterVertically){RadioButton(option==FifthCpcFixationOption.FROM_EVENT_DATE,{option=FifthCpcFixationOption.FROM_EVENT_DATE});Text("From Date of Event")};Row(verticalAlignment=Alignment.CenterVertically){RadioButton(option==FifthCpcFixationOption.FROM_DNI,{option=FifthCpcFixationOption.FROM_DNI});Text("From Date of DNI")};Box{OutlinedButton(onClick={menu=true},modifier=Modifier.fillMaxWidth()){Text(target?.title?:"Select higher 5th CPC scale",Modifier.weight(1f))};DropdownMenu(menu,{menu=false}){FifthToSixthCpcData.scales.filter{it.title!=scale.title}.forEach{s->DropdownMenuItem(text={Text(s.title)},onClick={target=s;menu=false})}}};val valid=eventDate!=null&&eventDate!!>=date&&eventDate!!<=fifthCpcEndDate()&&target!=null&&(option!=FifthCpcFixationOption.FROM_DNI||knownDniDate!=null);Button(onClick={val e=calculateFifthCpcEvent(eventDate!!,pay,scale,target!!,option,eventType,knownDniDate);events=events+e;increments=emptyList();showForm=false;eventDate=null;target=null},enabled=valid,modifier=Modifier.fillMaxWidth()){Text("Apply Event")}}}
        if(ready&&onContinueToSixth!=null)Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(Color(0xFFE8F5E9)),shape=RoundedCornerShape(16.dp)){Column(Modifier.padding(16.dp)){Text("Ready for 6th CPC",fontWeight=FontWeight.Bold,color=Color(0xFF1B5E20));Text("Continue with the latest 5th CPC pay state as on 01.01.2006.");Button(onClick={onContinueToSixth(scale,pay)},modifier=Modifier.fillMaxWidth()){Text("Continue to 6th CPC")}}}
    }
    if(picker){val s=rememberDatePickerState(initialSelectedDateMillis=eventDate?:date);DatePickerDialog(onDismissRequest={picker=false},confirmButton={TextButton(onClick={eventDate=s.selectedDateMillis;picker=false}){Text("OK")}},dismissButton={TextButton(onClick={picker=false}){Text("Cancel")}}){DatePicker(s)}}
}
private fun fmt(d:Long)=SimpleDateFormat("dd MMMM yyyy",Locale.ENGLISH).format(Date(d))
private fun money(v:Int)=NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")).format(v)
