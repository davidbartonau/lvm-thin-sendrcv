# LVMSpaceCheck
If you don't want to autoextend your thin partitions e.g. because you want to be emailed then LVMSpaceCheck is for you

Run the command once and then exit.  Suitable for a cron job.
```
java -cp lvm-thin-sendrcv/lvm-thin-sendrcv.jar oneit.lvmsendrcv.service.LVMSpaceCheck
```

Run the command every few seconds.  Suitable for running as a daemon.  Need a script for restarting ...
```
java -cp lvm-thin-sendrcv/lvm-thin-sendrcv.jar oneit.lvmsendrcv.service.LVMSpaceCheck --checkInterval 10
```
## When Space is Exceeded
When the configured space is exceeded, an alert email is sent on the 1, 10, 100, 1000th failure.

When the system returns to normal, the error count is reverted.

### Autoresizing
TODO.  Would be nice to have

## Help
Run with -h for help


## Configuration
The utility uses `/etc/lvmsendrcv/lvmsendrcv.conf`
```
# Both data and meta limit must be set for each thin pool
monitor.[vgname]/[lvname].dataLimit=0..100
monitor.[vgname]/[lvname].metaimit=0..100

# 2 Examples below
monitor.volg/volg-thinpool.dataLimit=70
monitor.volg/volg-thinpool.metaLimit=70

monitor.vgreplica/vgreplica-thinpoolreplica.dataLimit=50
monitor.vgreplica/vgreplica-thinpoolreplica.metaLimit=50
```

## Results
The system generates some information to stdout

If the system exceeds the limit, then an email will be sent like:
```
Subject: [lvmsendrcv] DATA Limit exceeded volg-thinpool
Volume:volg/volg-thinpool : DATA
Usage:78.49 > 70.0
Count:10
lvextend -L+1G volg/volg-thinpool
```
