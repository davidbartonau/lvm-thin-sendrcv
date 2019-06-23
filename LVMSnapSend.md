# LVMSnapSend
Currently in a very raw state, batteries NOT included.

**You must never write directly to the destination LV**.

## Basic Usage ###
Assuming that 


## Example Testing
This is an example setting up 2 VGs and LVs on the same server for easier testing.

### Set up thin LVM - SOURCE
```
apt install -y thin-provisioning-tools
apt install -y python
wget https://raw.githubusercontent.com/theraser/blocksync/master/blocksync.py

LV=thin_volume
VG=volg
THINPOOL=${VG}-thinpool
THINSIZE=7G   # Slightly smaller than the actual device.  Allow for resizing.
LVSIZE=3G
DEVICE=xvdf

pvcreate /dev/${DEVICE}
vgcreate ${VG} /dev/${DEVICE}

lvcreate -L ${THINSIZE} --thinpool ${THINPOOL}  ${VG} 

# Review the volume and metadata
lvdisplay ${VG}/${THINPOOL}

lvcreate -V ${LVSIZE} --thin -n ${LV} ${VG}/${THINPOOL}
```

### Set up thin LVM - REPLICA
```
LV=thinv_replica
VG=vgreplica
THINPOOL=${VG}-thinpoolreplica
THINSIZE=7G   # Slightly smaller than the actual device.  Allow for resizing.
LVSIZE=3G
DEVICE=xvdg

pvcreate /dev/${DEVICE}
vgcreate ${VG} /dev/${DEVICE}

lvcreate -L ${THINSIZE} --thinpool ${THINPOOL}  ${VG} 

# Review the volume and metadata
lvdisplay ${VG}/${THINPOOL}

lvcreate -V ${LVSIZE} --thin -n ${LV} ${VG}/${THINPOOL}
```

## Set up a file system and write to it
```
mkfs.ext4 /dev/${VG}/${LV}
mkdir /mnt/${VG}-${LV}
mount /dev/${VG}/${LV} /mnt/${VG}-${LV}/
rsync -av /etc /mnt/${VG}-${LV}/
```

## Run the initial synchronisation
```
java -cp lvm-thin-sendrcv/lvm-thin-sendrcv.jar oneit.lvmsendrcv.LVMSnapSend --vg volg --lv thin_volume | ssh root@localhost "java -cp lvm-thin-sendrcv/lvm-thin-sendrcv.jar oneit.lvmsendrcv.DDRandomReceive --bs 64 --of /dev/vgreplica/thinv_replica"

# There will be an error, because the initial sync must be done manually
lvcreate -s -n thin_volume_thinsendrcv_20190623_0554 volg/thin_volume
lvchange -ay -Ky /dev/volg/thin_volume_thinsendrcv_20190623_0554
python blocksync.py -c aes128-ctr /dev/volg/thin_volume_thinsendrcv_20190623_0554 root@localhost /dev/vgreplica/thinv_replica

# Check that the snapshots are created
lvs
md5sum /dev/volg/thin_volume_thinsendrcv_* /dev/vgreplica/thinv_replica
```

## Regular Syncronisation
```
rsync -av /usr/bin /mnt/${VG}-${LV}/usr/
java -cp lvm-thin-sendrcv/lvm-thin-sendrcv.jar oneit.lvmsendrcv.LVMSnapSend --vg volg --lv thin_volume | ssh -C root@localhost "java -cp lvm-thin-sendrcv/lvm-thin-sendrcv.jar oneit.lvmsendrcv.DDRandomReceive --bs 64 --of /dev/vgreplica/thinv_replica"

rsync -av /var /mnt/${VG}-${LV}/
java -cp lvm-thin-sendrcv/lvm-thin-sendrcv.jar oneit.lvmsendrcv.LVMSnapSend --vg volg --lv thin_volume | ssh -C root@localhost "java -cp lvm-thin-sendrcv/lvm-thin-sendrcv.jar oneit.lvmsendrcv.DDRandomReceive --bs 64 --of /dev/vgreplica/thinv_replica"

rsync -av /usr/lib /mnt/volg-thin_volume/usr/
java -cp lvm-thin-sendrcv/lvm-thin-sendrcv.jar oneit.lvmsendrcv.LVMSnapSend --vg volg --lv thin_volume | ssh -C root@localhost "java -cp lvm-thin-sendrcv/lvm-thin-sendrcv.jar oneit.lvmsendrcv.DDRandomReceive --bs 64 --of /dev/vgreplica/thinv_replica"

# Verify that it all worked
lvchange -ay -Ky /dev/volg/thin_volume_thinsendrcv_20190623_0610
md5sum /dev/volg/thin_volume_thinsendrcv_* /dev/vgreplica/thinv_replica
```



## Debugging Problems
### Test thin lvm is working properly
```
dmsetup message /dev/mapper/volg-volg--thinpool-tpool 0 reserve_metadata_snap
thin_delta  -m --snap1 $(lvs --noheadings -o thin_id volg/thin_volume_snap1) --snap2 $(lvs --noheadings -o thin_id volg/thin_volume_snap2) /dev/mapper/volg-volg--thinpool_tmeta
dmsetup message /dev/mapper/volg-volg--thinpool-tpool 0 release_metadata_snap
```

### Run it in 2 parts
```
java -cp lvm-thin-sendrcv/lvm-thin-sendrcv.jar oneit.lvmsendrcv.LVMSnapSend --vg volg --s1 thin_volume_snap1 --s2 thin_volume_snap2 > /tmp/diff.ddxml

# Currently split into 2 parts and I had to hardcode the block size at the destination
cat /tmp/diff.ddxml | java -cp lvm-thin-sendrcv/lvm-thin-sendrcv.jar oneit.lvmsendrcv.DDRandomServe -bs 64 -of /dev/other.device

```
