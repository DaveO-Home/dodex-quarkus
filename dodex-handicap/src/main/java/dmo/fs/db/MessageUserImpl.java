package dmo.fs.db;

import java.sql.Timestamp;
import java.util.Date;
import java.util.Optional;

public class MessageUserImpl implements MessageUser {

    protected Long id;
    protected String name;
    protected String password;
    protected String ip;
    protected Timestamp lastLogin;

    @Override
    public void setId(final Long id) {
        if (id instanceof Long) {
            this.id = id;
        } else {
            this.id = Long.parseLong(id.toString());
        }
    }

    @Override
    public void setName(final String name) {
        this.name = name;
    }

    @Override
    public void setPassword(final String password) {
        this.password = password;
    }

    @Override
    public void setIp(final String ip) {
        this.ip = ip;
    }

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getIp() {
        return ip;
    }

    @Override
    public <T>void setLastLogin(T lastLogin) {
        Optional<?> login = Optional.of(lastLogin);
        Optional<Timestamp> loginTimestamp = login
            .filter(Timestamp.class::isInstance)
            .map(Timestamp.class::cast);
        Optional<Long> loginLong = login
            .filter(Long.class::isInstance)
            .map(Long.class::cast);
        if(loginTimestamp.isPresent()) {
            this.lastLogin = loginTimestamp.get();
        } else if(loginLong.isPresent()) {
            this.lastLogin = new Timestamp(loginLong.get());
        } else {
            Optional<Date> loginDate = login
                .filter(Date.class::isInstance)
                .map(Date.class::cast);
            loginDate.ifPresent(date -> this.lastLogin = new Timestamp(date.getTime()));
        }
    }

    @Override
    public Timestamp getLastLogin() {
        return  lastLogin;
    }

    @Override
    public String toString() {
        return String.format("%s, %s, %s, %s, %s", id, name, password, ip, lastLogin);
    }
}