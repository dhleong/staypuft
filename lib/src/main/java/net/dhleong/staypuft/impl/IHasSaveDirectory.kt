package net.dhleong.staypuft.impl

import java.io.File

/**
 * @author dhleong
 */
interface IHasSaveDirectory {

    /**
     * File pointing to the directory we should
     *  save our expansion files into
     */
    fun getExpansionFilesDirectory(): File

}