//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.volmit.fuse.fabric.util;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class FolderWatcher extends FileWatcher {
    private Map<File, FolderWatcher> watchers;
    private List<File> changed;
    private List<File> created;
    private List<File> deleted;

    public FolderWatcher(File file) {
        super(file);
    }

    protected void readProperties() {
        if(this.watchers == null) {
            this.watchers = new HashMap();
            this.changed = new ArrayList();
            this.created = new ArrayList();
            this.deleted = new ArrayList();
        }

        if(this.file.isDirectory()) {
            File[] var1 = this.file.listFiles();
            int var2 = var1.length;

            for(int var3 = 0; var3 < var2; ++var3) {
                File i = var1[var3];
                if(!this.watchers.containsKey(i)) {
                    this.watchers.put(i, new FolderWatcher(i));
                }
            }

            Iterator<File> var5 = new ArrayList<>(this.watchers.keySet()).iterator();

            while(var5.hasNext()) {
                File i = var5.next();
                if(!i.exists()) {
                    this.watchers.remove(i);
                }
            }
        } else {
            super.readProperties();
        }

    }

    public boolean checkModified() {
        this.changed.clear();
        this.created.clear();
        this.deleted.clear();
        if(!this.file.isDirectory()) {
            return super.checkModified();
        } else {
            Map<File, FolderWatcher> w = new HashMap<>();
            w.putAll(this.watchers);
            this.readProperties();
            Iterator var2 = w.keySet().iterator();

            File i;
            while(var2.hasNext()) {
                i = (File) var2.next();
                if(!this.watchers.containsKey(i)) {
                    this.deleted.add(i);
                }
            }

            var2 = this.watchers.keySet().iterator();

            while(var2.hasNext()) {
                i = (File) var2.next();
                if(!w.containsKey(i)) {
                    this.created.add(i);
                } else {
                    FolderWatcher fw = this.watchers.get(i);
                    if(fw.checkModified()) {
                        this.changed.add(fw.file);
                    }

                    this.changed.addAll(fw.getChanged());
                    this.created.addAll(fw.getCreated());
                    this.deleted.addAll(fw.getDeleted());
                }
            }

            return !this.changed.isEmpty() || !this.created.isEmpty() || !this.deleted.isEmpty();
        }
    }

    public boolean checkModifiedFast() {
        if(this.watchers != null && !this.watchers.isEmpty()) {
            this.changed.clear();
            this.created.clear();
            this.deleted.clear();
            if(!this.file.isDirectory()) {
                return super.checkModified();
            } else {
                Iterator var1 = this.watchers.keySet().iterator();

                while(var1.hasNext()) {
                    File i = (File) var1.next();
                    FolderWatcher fw = this.watchers.get(i);
                    if(fw.checkModifiedFast()) {
                        this.changed.add(fw.file);
                    }

                    this.changed.addAll(fw.getChanged());
                    this.created.addAll(fw.getCreated());
                    this.deleted.addAll(fw.getDeleted());
                }

                return !this.changed.isEmpty() || !this.created.isEmpty() || !this.deleted.isEmpty();
            }
        } else {
            return this.checkModified();
        }
    }

    public Map<File, FolderWatcher> getWatchers() {
        return this.watchers;
    }

    public List<File> getChanged() {
        return this.changed;
    }

    public List<File> getCreated() {
        return this.created;
    }

    public List<File> getDeleted() {
        return this.deleted;
    }

    public void clear() {
        this.watchers.clear();
        this.changed.clear();
        this.deleted.clear();
        this.created.clear();
    }
}
