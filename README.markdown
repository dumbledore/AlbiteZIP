# Using the API

## Reading ZIP Files
    void readZIP() throws IOException {

        /*
         * Obtain a file for random reading
         */
        RandomReadingFile rrf =
                new RandomReadingFile("file:///root1/test.zip");

        try {
            /*
             * Load the ZIP file
             */
            ZipFile zipfile = new ZipFile(rrf);

            try {
                /*
                 * Get an enumeration of all the entries in the ZIP archive
                 */
                Enumeration e = zipfile.entries();

                /*
                 * Iterate over the entries and write their name,
                 * size, compressed size and CRC
                 */
                while (e.hasMoreElements()) {
                    ZipEntry ze = (ZipEntry) e.nextElement();
                    System.out.println(
                            ze.getName() + ": " +
                            ze.getSize() + " -> " +
                            ze.getCompressedSize() + ", CRC: " +
                            ze.getCrc()
                            );
                }

                /*
                 * Get an entry from the ZIP
                 */
                ZipEntry ze = zipfile.getEntry("some_compressed_file.txt");

                /*
                 * Get an input stream of the file contents
                 */
                DataInputStream din = zipfile.getInputStream(ze);
                try {
                    /*
                     * Read file's contents to a byte[] array
                     */
                    byte[] contents = new byte[ze.getSize()];
                    din.readFully();
                } finally {
                    din.close();
                }
            } finally {
                zipfile.close();
            }
        } finally {
            rrf.close();
        }
    }

## Writing ZIP Files
    void writeZIP() throws IOException {

        /*
         * Open  / Create output zip file
         */
        FileConnection fout =
                (FileConnection) Connector.open("file:///root1/output.zip");

        try {
            if (!fout.exists()) {
                fout.create();
            }

            /*
             * Obtain ouput stream for writing
             */
            ZipOutputStream zos = new ZipOutputStream(fout.openOutputStream());

            try {
                /*
                 * Create an entry (a file/directory header in the ZIP archive)
                 */
                ZipEntry ze = new ZipEntry("file_to_be_zipped.txt");

                /*
                 * Put the entry in the zip stream
                 */
                zos.putNextEntry(ze);

                /*
                 * Lets have some sample byte for output
                 */
                byte[] buf = "This is a sample string.".getBytes();

                /*
                 * Write the data to the output
                 */
                zos.write(buf);

                /*
                 * Close resources
                 */
                zos.closeEntry();
            } finally {
                zos.close();
            }
        } finally {
            fout.close();
        }
    }
