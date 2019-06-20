# LVMSnapSend
Currently in a very raw state, batteries NOT included.

## Set up thin LVM
```
apt-get install -y thin-provisioning-tools

LV=thin_volume
VG=volg
THINSIZE=THINSIZE=9.8G
LVSIZE=6G

pvcreate /dev/xvdf
vgcreate ${VG} /dev/xvdf

lvcreate -L ${THINSIZE} --thinpool ${VG}-thinpool  ${VG} 
lvcreate -V ${LVSIZE} --thin -n ${LV} ${VG}/${VG}-thinpool
```

## Set up a file system and write to it
```
mkfs.ext4 /dev/${VG}/${LV}
mkdir /mnt/${VG}-${LV}
mount /dev/${VG}/${LV} /mnt/${VG}-${LV}/

lvcreate -s -n ${LV}_snap1 ${VG}/${LV}

# Write to the file system /mnt/${VG}-${LV}/

lvcreate -s -n ${LV}_snap2 ${VG}/${LV}
```

## Test thin lvm is working properly
```
dmsetup message /dev/mapper/volg-volg--thinpool-tpool 0 reserve_metadata_snap
thin_delta  -m --snap1 $(lvs --noheadings -o thin_id volg/thin_volume_snap1) --snap2 $(lvs --noheadings -o thin_id volg/thin_volume_snap2) /dev/mapper/volg-volg--thinpool_tmeta
dmsetup message /dev/mapper/volg-volg--thinpool-tpool 0 release_metadata_snap
```

## Run it in 2 parts
```
java -cp lvm-thin-sendrcv/lvm-thin-sendrcv.jar oneit.lvmsendrcv.LVMSnapSend --vg volg --s1 thin_volume_snap1 --s2 thin_volume_snap2 > /tmp/diff.ddxml

# Currently split into 2 parts and I had to hardcode the block size at the destination
cat /tmp/diff.ddxml | java -cp lvm-thin-sendrcv/lvm-thin-sendrcv.jar oneit.lvmsendrcv.DDRandomServe -bs 64 -of /dev/other.device

```
