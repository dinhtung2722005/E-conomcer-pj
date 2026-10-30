"use strict";
(() => {
  const $ = id => document.getElementById(id);
  const root = "/api/admin/wallet-ops";
  let token = "", tab = "transfers", page = 1, pages = 1, pendingAction;
  const groups = {
    transfers: ["Giao dịch ngân hàng", [["id","Mã yêu cầu"],["transactionId","Giao dịch"],["walletId","Ví"],["type","Loại"],["amount","Số tiền"],["status","Trạng thái"],["attempts","Lần xử lý"],["slaBreached","Quá hạn"],["lastError","Cần kiểm tra"]]],
    holds: ["Khoản đang giữ", [["id","Mã giữ"],["walletId","Ví"],["orderId","Đơn"],["source","Nguồn"],["remaining","Còn giữ"],["status","Trạng thái"],["slaBreached","Quá hạn"]]],
    refunds: ["Hồ sơ hoàn tiền", [["id","Hồ sơ"],["orderId","Đơn"],["status","Trạng thái"],["reason","Lý do"],["createdAt","Tiếp nhận"],["slaBreached","Quá hạn"]]],
    settlements: ["Quyết toán", [["orderId","Đơn"],["amount","Số tiền"],["availableAt","Đủ điều kiện lúc"],["attempts","Lần xử lý"],["lastError","Cần kiểm tra"],["slaBreached","Quá hạn"]]],
    alerts: ["Cảnh báo", [["id","Mã"],["category","Nhóm"],["reference","Tham chiếu"],["message","Nội dung"],["status","Trạng thái"],["lastSeenAt","Ghi nhận gần nhất"]]],
    audit: ["Lịch sử thao tác", [["id","Mã"],["actorId","Người thao tác"],["action","Thao tác"],["reference","Tham chiếu"],["reason","Lý do"],["createdAt","Thời điểm"]]]
  };
  async function api(url, body) {
    const headers = {"Content-Type":"application/json"}; if (token) headers.Authorization = `Bearer ${token}`;
    const response = await fetch(url, {credentials:"same-origin", headers, method:body ? "POST" : "GET", ...(body ? {body:JSON.stringify(body)} : {})});
    const result = await response.json();
    if (!response.ok) throw new Error(response.status === 401 ? "Phiên đăng nhập không hợp lệ hoặc đã hết hạn." : response.status === 403 ? "Tài khoản cần có quyền ADMIN." : result.message || "Không thể xử lý yêu cầu.");
    return result.result;
  }
  function message(text, error=false) { $("message").textContent=text; $("message").className=error ? "error" : ""; }
  function element(name, text) { const el=document.createElement(name); if(text!==undefined)el.textContent=text; return el; }
  function openAction(title, url, payload) { pendingAction={url,payload}; $("action-title").textContent=title; $("reason").value=""; $("action-error").textContent=""; $("action").showModal(); }
  function button(cell, text, action) { const b=element("button",text); b.type="button"; b.addEventListener("click",action); cell.append(b); }
  async function load() {
    try {
      message("Đang tải…");
      const [summary, list] = await Promise.all([api(`${root}/summary`), api(`${root}/${tab}?page=${page}&size=20${tab==="transfers"&&$("overdue").checked?"&overdue=true":""}`)]);
      $("summary").replaceChildren();
      [["unfinishedTransfers","Giao dịch đang chờ"],["pendingRefunds","Hồ sơ chờ duyệt"],["openAlerts","Cảnh báo mới"],["unbalancedJournals","Bút toán cần kiểm tra"]].forEach(([key,label])=>{const card=element("div");card.className="card";card.append(element("span",label),element("strong",summary[key]));$("summary").append(card);});
      $("list-title").textContent=groups[tab][0]; $("overdue-label").hidden=tab!=="transfers";
      const tr=element("tr"); groups[tab][1].forEach(([,label])=>tr.append(element("th",label))); tr.append(element("th","Thao tác")); $("head").replaceChildren(tr); $("rows").replaceChildren();
      list.data.forEach(row=>{
        const tr=element("tr"); groups[tab][1].forEach(([key])=>{const value=row[key];const td=element("td",typeof value==="boolean"?(value?"Có":"Không"):value??"—");if(key==="slaBreached"&&value)td.className="warning";tr.append(td);});
        const actions=element("td");
        if(tab==="transfers"&&!row.finished)button(actions,"Thử lại",()=>openAction("Lên lịch kiểm tra lại giao dịch",`${root}/transfers/${row.id}/retry`,{}));
        if(tab==="settlements")button(actions,"Thử lại",()=>openAction("Lên lịch quyết toán lại",`${root}/settlements/${row.orderId}/retry`,{}));
        if(tab==="refunds"&&row.status==="REQUESTED"){
          button(actions,"Duyệt hoàn",()=>openAction("Xác nhận đã kiểm tra hồ sơ/hàng trả",`/api/admin/orders/${row.orderId}/refund/decision`,{approved:true}));
          button(actions,"Từ chối",()=>openAction("Từ chối yêu cầu hoàn",`/api/admin/orders/${row.orderId}/refund/decision`,{approved:false}));
        }
        if(tab==="alerts"){
          button(actions,"Tiếp nhận",()=>openAction("Tiếp nhận cảnh báo",`${root}/alerts/${row.id}/review`,{status:"ACKNOWLEDGED"}));
          button(actions,"Đã xử lý",()=>openAction("Xác nhận xử lý cảnh báo",`${root}/alerts/${row.id}/review`,{status:"RESOLVED"}));
        }
        tr.append(actions); $("rows").append(tr);
      });
      if(!list.data.length){const tr=element("tr"),td=element("td","Không có dữ liệu phù hợp.");td.colSpan=groups[tab][1].length+1;td.className="empty";tr.append(td);$("rows").append(tr);}
      pages=Math.max(1,list.totalPages); $("count").textContent=`${list.totalElements} bản ghi`; $("page").textContent=`${page} / ${pages}`;$("previous").disabled=page<=1;$("next").disabled=page>=pages;message("Đã cập nhật.");
    } catch(error) { message(error.message,true); }
  }
  Object.entries(groups).forEach(([key,[label]])=>{const b=element("button",label);b.type="button";b.className=key===tab?"active":"";b.onclick=()=>{tab=key;page=1;[...$("tabs").children].forEach(x=>x.className="");b.className="active";load();};$("tabs").append(b);});
  $("session").onsubmit=e=>{e.preventDefault();token=$("token").value.trim();$("token").value="";page=1;load();};
  $("reload").onclick=load;$("overdue").onchange=()=>{page=1;load();};$("previous").onclick=()=>{if(page>1){page--;load();}};$("next").onclick=()=>{if(page<pages){page++;load();}};
  $("dismiss").onclick=()=>$("action").close();
  $("action-form").onsubmit=async e=>{e.preventDefault();if(!pendingAction)return;$("confirm").disabled=true;try{await api(pendingAction.url,{...pendingAction.payload,reason:$("reason").value.trim()});$("action").close();await load();}catch(error){$("action-error").textContent=error.message;}finally{$("confirm").disabled=false;}};
})();
