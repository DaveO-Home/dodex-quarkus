package dmo.fs.utils;

import java.io.Serializable;
import java.sql.Array;
import java.util.HashMap;
import java.util.Map;

public class Group implements Serializable {
  public String groupMessage;
  public String name;
  public String groupName;
  public String groupOwner;
  public String ownerId;
  public Integer status;
  public Integer id;
  public String created;
  public String updated;
  public String errorMessage;
  public String members;

  public Group() {}
  public Group( String groupMessage,
                String name,
                String groupName,
                String groupOwner,
                String ownerId,
                Integer status,
                Integer id,
                String created,
                String updated,
                String errorMessage,
                String members) {
    this.groupMessage = groupMessage;
    this.name = name;
    this.groupName = groupName;
    this.groupOwner = groupOwner;
    this.ownerId = ownerId;
    this.status = status;
    this.id = id;
    this.created = created;
    this.updated = updated;
    this.errorMessage = errorMessage;
    this.members = members;
  }
  public Map<String, Object> getMap() {
    Map<String, Object> groupMap = new HashMap<>();
    groupMap.put("groupMessage", this.groupMessage);
    groupMap.put("name", this.name);
    groupMap.put("groupName", this.groupName);
    groupMap.put("groupOwner", this.groupOwner);
    groupMap.put("ownerId", this.ownerId);
    groupMap.put("status", this.status);
    groupMap.put("id", this.id);
    groupMap.put("created", this.created);
    groupMap.put("updated", this.updated);
    groupMap.put("errorMessage", this.errorMessage);
    groupMap.put("members", this.members);
    return groupMap;
  }
}
